import crypto from 'node:crypto';
import { conflict } from '../utils/errors.js';

const nowIso = () => new Date().toISOString();
const clone = (value) => (value === undefined ? undefined : JSON.parse(JSON.stringify(value)));

/**
 * In-memory repository with the same contract as the PostgreSQL implementation.
 *
 * It exists so the API, payment-callback and duplicate-protection behaviour can be
 * exercised deterministically in CI without a database. It enforces the same
 * uniqueness rules the SQL schema enforces.
 */
export function createMemoryRepository(seed = {}) {
  const state = {
    admins: [...(seed.admins ?? [])],
    devices: [...(seed.devices ?? [])],
    bundles: [...(seed.bundles ?? [])],
    workflows: [...(seed.workflows ?? [])],
    transactions: [...(seed.transactions ?? [])],
    payments: [...(seed.payments ?? [])],
    ussdJobs: [...(seed.ussdJobs ?? [])],
    auditLogs: []
  };

  return {
    kind: 'memory',

    admins: {
      async create({ email, name, role = 'operator', passwordHash }) {
        if (state.admins.some((a) => a.email.toLowerCase() === email.toLowerCase())) {
          throw conflict('An administrator with that email already exists', 'email_taken');
        }
        const admin = {
          id: crypto.randomUUID(),
          email,
          name,
          role,
          passwordHash,
          disabled: false,
          lastLoginAt: null,
          createdAt: nowIso()
        };
        state.admins.push(admin);
        return clone(admin);
      },
      async findByEmail(email) {
        const admin = state.admins.find((a) => a.email.toLowerCase() === String(email).toLowerCase());
        return clone(admin ?? null);
      },
      async findById(id) {
        return clone(state.admins.find((a) => a.id === id) ?? null);
      },
      async touchLogin(id) {
        const admin = state.admins.find((a) => a.id === id);
        if (admin) admin.lastLoginAt = nowIso();
      },
      async count() {
        return state.admins.length;
      }
    },

    devices: {
      async create({ id, label, apiKeyHash }) {
        const device = {
          id: id ?? crypto.randomUUID(),
          label,
          apiKeyHash,
          enabled: true,
          lastSeenAt: null,
          createdAt: nowIso()
        };
        state.devices.push(device);
        return clone(device);
      },
      async findById(id) {
        return clone(state.devices.find((d) => d.id === id) ?? null);
      },
      async list() {
        return state.devices.map((d) => clone({ ...d, apiKeyHash: undefined }));
      },
      async setEnabled(id, enabled) {
        const device = state.devices.find((d) => d.id === id);
        if (!device) return null;
        device.enabled = enabled;
        return clone(device);
      },
      async touch(id) {
        const device = state.devices.find((d) => d.id === id);
        if (device) device.lastSeenAt = nowIso();
      }
    },

    bundles: {
      async list({ network, includeDisabled = false } = {}) {
        return state.bundles
          .filter((b) => !b.deletedAt)
          .filter((b) => (network ? b.network === network : true))
          .filter((b) => (includeDisabled ? true : b.enabled))
          .sort((a, b) => a.sortOrder - b.sortOrder || a.dataSizeMb - b.dataSizeMb)
          .map(clone);
      },
      async findById(id) {
        const bundle = state.bundles.find((b) => b.id === id && !b.deletedAt);
        return clone(bundle ?? null);
      },
      async create(data) {
        const bundle = { id: crypto.randomUUID(), createdAt: nowIso(), ...data };
        state.bundles.push(bundle);
        return clone(bundle);
      },
      async update(id, patch) {
        const bundle = state.bundles.find((b) => b.id === id && !b.deletedAt);
        if (!bundle) return null;
        Object.assign(bundle, patch, { updatedAt: nowIso() });
        return clone(bundle);
      },
      async softDelete(id) {
        const bundle = state.bundles.find((b) => b.id === id);
        if (!bundle) return false;
        bundle.deletedAt = nowIso();
        bundle.enabled = false;
        return true;
      }
    },

    workflows: {
      async list({ network } = {}) {
        return state.workflows
          .filter((w) => (network ? w.network === network : true))
          .map(clone);
      },
      async findById(id) {
        return clone(state.workflows.find((w) => w.id === id) ?? null);
      },
      async findEnabledByNetwork(network) {
        return clone(
          state.workflows.find((w) => w.network === network && w.enabled && w.verifiedBy) ?? null
        );
      },
      async create(data) {
        const workflow = { id: crypto.randomUUID(), version: 1, createdAt: nowIso(), ...data };
        state.workflows.push(workflow);
        return clone(workflow);
      },
      async update(id, patch) {
        const workflow = state.workflows.find((w) => w.id === id);
        if (!workflow) return null;
        Object.assign(workflow, patch, { updatedAt: nowIso() });
        return clone(workflow);
      }
    },

    transactions: {
      async create(data) {
        if (state.transactions.some((t) => t.idempotencyKey === data.idempotencyKey)) {
          const error = conflict('Duplicate idempotency key', 'duplicate_idempotency_key');
          throw error;
        }
        const transaction = {
          id: crypto.randomUUID(),
          createdAt: nowIso(),
          updatedAt: nowIso(),
          paymentStatus: 'PENDING',
          purchaseStatus: 'QUEUED',
          manualConfirmationRequired: false,
          ...data
        };
        state.transactions.push(transaction);
        return clone(transaction);
      },
      async findById(id) {
        return clone(state.transactions.find((t) => t.id === id) ?? null);
      },
      async findByIdempotencyKey(key) {
        return clone(state.transactions.find((t) => t.idempotencyKey === key) ?? null);
      },
      async findActiveDuplicate({ msisdn, bundleId, since }) {
        const found = state.transactions.find(
          (t) =>
            t.customerMsisdn === msisdn &&
            t.bundleId === bundleId &&
            new Date(t.createdAt) >= new Date(since) &&
            (t.paymentStatus === 'PENDING' ||
              ['QUEUED', 'RUNNING', 'AWAITING_MANUAL_CONFIRMATION'].includes(t.purchaseStatus))
        );
        return clone(found ?? null);
      },
      async list({ limit = 50, offset = 0, status } = {}) {
        return state.transactions
          .filter((t) => (status ? t.purchaseStatus === status : true))
          .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
          .slice(offset, offset + limit)
          .map(clone);
      },
      async update(id, patch) {
        const transaction = state.transactions.find((t) => t.id === id);
        if (!transaction) return null;
        Object.assign(transaction, patch, { updatedAt: nowIso() });
        return clone(transaction);
      },
      async summary() {
        const paid = state.transactions.filter((t) => t.paymentStatus === 'PAID');
        const startOfDay = new Date();
        startOfDay.setHours(0, 0, 0, 0);
        return {
          totalSalesCents: paid.reduce((sum, t) => sum + Number(t.amountCents), 0),
          todayRevenueCents: paid
            .filter((t) => new Date(t.createdAt) >= startOfDay)
            .reduce((sum, t) => sum + Number(t.amountCents), 0),
          totalTransactions: state.transactions.length,
          successfulTransactions: state.transactions.filter((t) => t.purchaseStatus === 'SUCCESS').length,
          failedTransactions: state.transactions.filter((t) => t.purchaseStatus === 'FAILED').length,
          pendingTransactions: state.transactions.filter((t) => t.paymentStatus === 'PENDING').length,
          activeBundles: state.bundles.filter((b) => b.enabled && !b.deletedAt).length
        };
      }
    },

    payments: {
      async create(data) {
        const payment = { id: crypto.randomUUID(), createdAt: nowIso(), ...data };
        state.payments.push(payment);
        return clone(payment);
      },
      async findByCheckoutId(checkoutRequestId) {
        return clone(state.payments.find((p) => p.checkoutRequestId === checkoutRequestId) ?? null);
      },
      async update(id, patch) {
        const payment = state.payments.find((p) => p.id === id);
        if (!payment) return null;
        Object.assign(payment, patch, { updatedAt: nowIso() });
        return clone(payment);
      },
      async listByTransaction(transactionId) {
        return state.payments.filter((p) => p.transactionId === transactionId).map(clone);
      }
    },

    ussdJobs: {
      async create(data) {
        if (state.ussdJobs.some((j) => j.transactionId === data.transactionId)) {
          throw conflict('A delivery job already exists for this transaction', 'job_exists');
        }
        const job = {
          id: crypto.randomUUID(),
          createdAt: nowIso(),
          updatedAt: nowIso(),
          status: 'QUEUED',
          attemptCount: 0,
          maxAttempts: 3,
          nextAttemptAt: nowIso(),
          stepsLog: [],
          ...data
        };
        state.ussdJobs.push(job);
        return clone(job);
      },
      async claimNext({ deviceId, leaseSeconds = 120 }) {
        const now = new Date();
        const candidate = state.ussdJobs
          .filter((j) => ['QUEUED', 'AWAITING_MANUAL_CONFIRMATION'].includes(j.status))
          .filter((j) => new Date(j.nextAttemptAt) <= now)
          .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))[0];
        if (!candidate) return null;
        candidate.status = 'RUNNING';
        candidate.attemptCount += 1;
        candidate.claimedBy = deviceId ?? null;
        candidate.leaseExpiresAt = new Date(now.getTime() + leaseSeconds * 1000).toISOString();
        candidate.updatedAt = nowIso();
        return clone(candidate);
      },
      async findById(id) {
        return clone(state.ussdJobs.find((j) => j.id === id) ?? null);
      },
      async findByTransaction(transactionId) {
        return clone(state.ussdJobs.find((j) => j.transactionId === transactionId) ?? null);
      },
      async update(id, patch) {
        const job = state.ussdJobs.find((j) => j.id === id);
        if (!job) return null;
        Object.assign(job, patch, { updatedAt: nowIso() });
        return clone(job);
      },
      async list({ status, limit = 50 } = {}) {
        return state.ussdJobs
          .filter((j) => (status ? j.status === status : true))
          .slice(0, limit)
          .map(clone);
      }
    },

    audit: {
      async record(entry) {
        state.auditLogs.push({ id: state.auditLogs.length + 1, createdAt: nowIso(), ...entry });
      },
      async list({ limit = 100 } = {}) {
        return state.auditLogs.slice(-limit).reverse().map(clone);
      }
    },

    /** Test helper — not part of the production contract. */
    _state: state
  };
}
