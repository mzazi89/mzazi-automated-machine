import {
  badRequest,
  conflict,
  notFound,
  unprocessable
} from '../utils/errors.js';
import { randomReference } from '../utils/crypto.js';
import { normalizeMsisdn } from '../utils/msisdn.js';
import { assertTransition, PURCHASE_TRANSITIONS } from '../domain/stateMachine.js';

/**
 * Purchase orchestration.
 *
 * Guarantees:
 *  1. `Idempotency-Key` is mandatory; the same key always returns the same purchase.
 *  2. A second active sale for the same number+bundle inside the window is refused.
 *  3. A delivery job is only created by the payment service after PAID.
 */
export function createTransactionService({ repo, config, payments, audit }) {
  async function create({ body, idempotencyKey, device, ip, actor }) {
    if (!idempotencyKey || String(idempotencyKey).trim().length < 8) {
      throw badRequest(
        'An Idempotency-Key header (8+ characters) is required',
        'idempotency_key_required'
      );
    }
    const key = String(idempotencyKey).trim();

    const existing = await repo.transactions.findByIdempotencyKey(key);
    if (existing) {
      return { transaction: existing, replay: true };
    }

    const msisdn = normalizeMsisdn(body?.customerMsisdn);
    if (!msisdn) {
      throw unprocessable('Enter a valid Kenyan mobile number', 'invalid_msisdn');
    }

    if (!body?.bundleId) throw badRequest('bundleId is required', 'bundle_required');
    const bundle = await repo.bundles.findById(body.bundleId);
    if (!bundle || !bundle.enabled) {
      throw notFound('That bundle is not available for sale', 'bundle_unavailable');
    }

    const network = String(body?.network ?? bundle.network).toUpperCase();
    if (network !== bundle.network) {
      throw unprocessable('The selected network does not match the bundle', 'network_mismatch');
    }

    const since = new Date(
      Date.now() - config.duplicateWindowMinutes * 60 * 1000
    ).toISOString();
    const duplicate = await repo.transactions.findActiveDuplicate({
      msisdn,
      bundleId: bundle.id,
      since
    });
    if (duplicate) {
      throw conflict(
        'A purchase for this number and bundle is already in progress',
        'duplicate_active_purchase'
      );
    }

    const testMode = Boolean(body?.testMode) && config.testMode;

    let transaction;
    try {
      transaction = await repo.transactions.create({
        sellerId: actor?.type === 'admin' ? (actor.id ?? null) : null,
        reference: randomReference('MZ'),
        customerMsisdn: msisdn,
        network: bundle.network,
        bundleId: bundle.id,
        bundleName: bundle.name,
        bundleSizeMb: bundle.dataSizeMb,
        amountCents: bundle.sellingPriceCents,
        idempotencyKey: key,
        simSlot: Number.isInteger(body?.simSlot) ? body.simSlot : null,
        testMode,
        deviceId: device?.id ?? null
      });
    } catch (error) {
      if (error.code === 'duplicate_idempotency_key') {
        const replay = await repo.transactions.findByIdempotencyKey(key);
        if (replay) return { transaction: replay, replay: true };
      }
      throw error;
    }

    await audit?.record({
      actorType: actor?.type ?? (device ? 'device' : 'system'),
      actorId: actor?.id ?? device?.id ?? null,
      action: 'transaction.created',
      target: transaction.id,
      detail: { msisdn, bundleId: bundle.id, amountCents: bundle.sellingPriceCents, testMode },
      ip
    });

    if (testMode) {
      // Test mode never contacts Safaricom; the caller settles via /payments/test/simulate.
      return { transaction, replay: false, simulated: true };
    }

    const { transaction: withPayment, simulated } = await payments.initiateStkPush({
      transaction,
      phone: msisdn
    });

    return { transaction: withPayment, replay: false, simulated };
  }

  async function getById(id, scope) {
    const transaction = await repo.transactions.findById(id);
    if (!transaction) throw notFound('Transaction not found');
    if (scope?.role === 'seller' && transaction.sellerId !== scope.id) {
      // Do not confirm existence of another seller's sale.
      throw notFound('Transaction not found');
    }
    return transaction;
  }

  async function list({ limit = 50, offset = 0, status, scope } = {}) {
    const safeLimit = Math.min(Math.max(Number(limit) || 50, 1), 200);
    const safeOffset = Math.max(Number(offset) || 0, 0);
    // Sellers only ever see the sales they rang up; owners and terminals see all.
    const sellerId = scope?.role === 'seller' ? scope.id : undefined;
    const items = await repo.transactions.list({ limit: safeLimit, offset: safeOffset, status, sellerId });
    return { items, limit: safeLimit, offset: safeOffset };
  }

  async function cancel(id, actor = {}, scope) {
    const transaction = await repo.transactions.findById(id);
    if (!transaction) throw notFound('Transaction not found');
    if (scope?.role === 'seller' && transaction.sellerId !== scope.id) {
      throw notFound('Transaction not found');
    }
    if (transaction.paymentStatus === 'PAID') {
      throw conflict('A paid transaction cannot be cancelled; refund it instead', 'already_paid');
    }
    assertTransition(PURCHASE_TRANSITIONS, transaction.purchaseStatus, 'CANCELLED', 'purchase');
    const updated = await repo.transactions.update(id, {
      paymentStatus: transaction.paymentStatus === 'PENDING' ? 'CANCELLED' : transaction.paymentStatus,
      purchaseStatus: 'CANCELLED',
      failureReason: 'Cancelled by operator'
    });
    await audit?.record({
      actorType: actor.type ?? 'admin',
      actorId: actor.id ?? null,
      action: 'transaction.cancelled',
      target: id
    });
    return updated;
  }

  async function summary({ scope } = {}) {
    const data = await repo.transactions.summary({
      sellerId: scope?.role === 'seller' ? scope.id : undefined
    });
    const activeBundles = await repo.bundles.list({});
    return { ...data, activeBundles: activeBundles.length };
  }

  return { create, getById, list, cancel, summary };
}
