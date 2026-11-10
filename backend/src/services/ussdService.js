import { conflict, notFound, unprocessable } from '../utils/errors.js';
import { assertTransition, PURCHASE_TRANSITIONS } from '../domain/stateMachine.js';

const JOB_STATUSES = new Set([
  'QUEUED',
  'CLAIMED',
  'RUNNING',
  'AWAITING_MANUAL_CONFIRMATION',
  'SUCCESS',
  'FAILED',
  'CANCELLED',
  'DEAD_LETTER'
]);

/**
 * USSD delivery queue.
 *
 * A job can only be created for a transaction the backend has already marked PAID
 * (also enforced by a database trigger). Claiming uses a lease so two terminals can
 * never execute the same delivery.
 */
export function createUssdService({ repo, audit }) {
  async function createJobForTransaction(transaction) {
    if (transaction.paymentStatus !== 'PAID') {
      throw conflict(
        'A USSD job cannot be created before the payment is verified as PAID',
        'payment_not_verified'
      );
    }
    const workflow = await repo.workflows.findEnabledByNetwork(transaction.network);
    const job = await repo.ussdJobs.create({
      transactionId: transaction.id,
      network: transaction.network,
      workflowId: workflow?.id ?? null,
      simSlot: transaction.simSlot ?? 0
    });
    await audit?.record({
      actorType: 'system',
      action: 'ussd.job_created',
      target: job.id,
      detail: { transactionId: transaction.id, workflowId: workflow?.id ?? null }
    });
    return job;
  }

  async function claimNext(deviceId) {
    const job = await repo.ussdJobs.claimNext({ deviceId, leaseSeconds: 120 });
    if (!job) return null;

    const [transaction, workflow] = await Promise.all([
      repo.transactions.findById(job.transactionId),
      job.workflowId ? repo.workflows.findById(job.workflowId) : Promise.resolve(null)
    ]);

    // If the money moved backwards while queued, refuse to deliver.
    if (!transaction || transaction.paymentStatus !== 'PAID') {
      await repo.ussdJobs.update(job.id, {
        status: 'FAILED',
        lastError: 'Payment is no longer verified as PAID'
      });
      return null;
    }

    // Reflect execution in the purchase state machine, so later transitions
    // (SUCCESS / FAILED / AWAITING_MANUAL_CONFIRMATION) are legal.
    if (transaction.purchaseStatus === 'QUEUED') {
      const running = await repo.transactions.update(transaction.id, { purchaseStatus: 'RUNNING' });
      return { job, transaction: running, workflow };
    }

    return { job, transaction, workflow };
  }

  async function reportResult(jobId, payload, actor = {}) {
    const job = await repo.ussdJobs.findById(jobId);
    if (!job) throw notFound('Delivery job not found');

    const status = String(payload.status ?? '').toUpperCase();
    if (!JOB_STATUSES.has(status)) {
      throw unprocessable(`Unknown job status: ${payload.status}`);
    }
    if (['QUEUED', 'CLAIMED'].includes(status)) {
      throw unprocessable('A device cannot move a job back to a queued state');
    }

    const transaction = await repo.transactions.findById(job.transactionId);
    if (!transaction) throw notFound('Transaction for this job no longer exists');

    const steps = Array.isArray(payload.steps) ? payload.steps.slice(0, 20) : [];
    const now = new Date();

    if (status === 'SUCCESS') {
      // Never accept "success" without evidence of a carrier receipt.
      if (!payload.receiptText || String(payload.receiptText).trim().length < 4) {
        throw unprocessable(
          'A success report must include the carrier receipt text',
          'receipt_required'
        );
      }
      assertTransition(
        PURCHASE_TRANSITIONS,
        transaction.purchaseStatus,
        'SUCCESS',
        'purchase'
      );
      const updatedJob = await repo.ussdJobs.update(job.id, {
        status: 'SUCCESS',
        stepsLog: steps,
        lastError: null,
        leaseExpiresAt: null
      });
      const updatedTransaction = await repo.transactions.update(transaction.id, {
        purchaseStatus: 'SUCCESS',
        failureReason: null,
        manualConfirmationRequired: false
      });
      await audit?.record({
        actorType: 'device',
        actorId: actor.deviceId ?? null,
        action: 'ussd.succeeded',
        target: job.id,
        detail: { transactionId: transaction.id }
      });
      return { job: updatedJob, transaction: updatedTransaction };
    }

    if (status === 'AWAITING_MANUAL_CONFIRMATION') {
      assertTransition(
        PURCHASE_TRANSITIONS,
        transaction.purchaseStatus,
        'AWAITING_MANUAL_CONFIRMATION',
        'purchase'
      );
      const updatedJob = await repo.ussdJobs.update(job.id, {
        status: 'AWAITING_MANUAL_CONFIRMATION',
        stepsLog: steps,
        lastError: payload.error ?? 'Manual confirmation required',
        leaseExpiresAt: null
      });
      const updatedTransaction = await repo.transactions.update(transaction.id, {
        purchaseStatus: 'AWAITING_MANUAL_CONFIRMATION',
        manualConfirmationRequired: true,
        failureReason: payload.error ?? null
      });
      await audit?.record({
        actorType: 'device',
        actorId: actor.deviceId ?? null,
        action: 'ussd.manual_confirmation_required',
        target: job.id,
        detail: { reason: payload.error ?? null }
      });
      return { job: updatedJob, transaction: updatedTransaction };
    }

    // FAILED / CANCELLED / DEAD_LETTER
    const retryable =
      payload.retryable === true &&
      payload.failureCategory === 'NETWORK_ERROR' &&
      job.attemptCount < job.maxAttempts &&
      status === 'FAILED';

    if (retryable) {
      // The request never reached the carrier's application layer, so a retry cannot
      // double-deliver. Everything else is terminal.
      const nextAttempt = new Date(now.getTime() + 60_000 * job.attemptCount);
      const updatedJob = await repo.ussdJobs.update(job.id, {
        status: 'QUEUED',
        stepsLog: steps,
        lastError: payload.error ?? 'Transport error',
        nextAttemptAt: nextAttempt.toISOString(),
        leaseExpiresAt: null
      });
      await audit?.record({
        actorType: 'device',
        actorId: actor.deviceId ?? null,
        action: 'ussd.retry_scheduled',
        target: job.id,
        detail: { attempt: job.attemptCount, nextAttemptAt: nextAttempt.toISOString() }
      });
      return { job: updatedJob, transaction };
    }

    const finalStatus = status === 'CANCELLED' ? 'CANCELLED' : 'FAILED';
    assertTransition(PURCHASE_TRANSITIONS, transaction.purchaseStatus, finalStatus, 'purchase');

    const updatedJob = await repo.ussdJobs.update(job.id, {
      status: finalStatus,
      stepsLog: steps,
      lastError: payload.error ?? null,
      leaseExpiresAt: null
    });
    const updatedTransaction = await repo.transactions.update(transaction.id, {
      purchaseStatus: finalStatus,
      failureReason: payload.error ?? 'Delivery failed',
      manualConfirmationRequired: false
    });
    await audit?.record({
      actorType: 'device',
      actorId: actor.deviceId ?? null,
      action: 'ussd.failed',
      target: job.id,
      detail: { error: payload.error ?? null, category: payload.failureCategory ?? null }
    });
    return { job: updatedJob, transaction: updatedTransaction };
  }

  /** An operator records the outcome observed on the handset after a manual dial. */
  async function manualConfirm(jobId, payload, actor = {}) {
    const job = await repo.ussdJobs.findById(jobId);
    if (!job) throw notFound('Delivery job not found');

    const outcome = String(payload.outcome ?? '').toUpperCase();
    if (!['SUCCESS', 'FAILED'].includes(outcome)) {
      throw unprocessable('outcome must be SUCCESS or FAILED');
    }
    if (job.status === 'SUCCESS' || job.status === 'FAILED') {
      throw conflict('This delivery already has a final outcome', 'job_finalised');
    }

    const transaction = await repo.transactions.findById(job.transactionId);
    assertTransition(PURCHASE_TRANSITIONS, transaction.purchaseStatus, outcome, 'purchase');

    const updatedJob = await repo.ussdJobs.update(job.id, {
      status: outcome,
      lastError: payload.note ?? null,
      leaseExpiresAt: null
    });
    const updatedTransaction = await repo.transactions.update(transaction.id, {
      purchaseStatus: outcome,
      failureReason: outcome === 'FAILED' ? payload.note ?? 'Operator marked failure' : null,
      manualConfirmationRequired: false
    });

    await audit?.record({
      actorType: actor.adminId ? 'admin' : 'device',
      actorId: actor.adminId ?? actor.deviceId ?? null,
      action: `ussd.manual_${outcome.toLowerCase()}`,
      target: job.id,
      detail: { note: payload.note ?? null, carrierReference: payload.carrierReference ?? null }
    });
    return { job: updatedJob, transaction: updatedTransaction };
  }

  async function listJobs({ status, limit = 50 } = {}) {
    return repo.ussdJobs.list({ status, limit });
  }

  return { createJobForTransaction, claimNext, reportResult, manualConfirm, listJobs };
}
