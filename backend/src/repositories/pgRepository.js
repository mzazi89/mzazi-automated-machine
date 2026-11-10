import crypto from 'node:crypto';
import { conflict } from '../utils/errors.js';

const mapAdmin = (row) =>
  row && {
    id: row.id,
    email: row.email,
    name: row.name,
    role: row.role,
    passwordHash: row.password_hash,
    disabled: row.disabled,
    lastLoginAt: row.last_login_at,
    createdAt: row.created_at
  };

const mapDevice = (row) =>
  row && {
    id: row.id,
    label: row.label,
    apiKeyHash: row.api_key_hash,
    enabled: row.enabled,
    lastSeenAt: row.last_seen_at,
    createdAt: row.created_at
  };

const mapBundle = (row) =>
  row && {
    id: row.id,
    network: row.network,
    name: row.name,
    dataSizeMb: row.data_size_mb,
    sellingPriceCents: Number(row.selling_price_cents),
    costPriceCents: Number(row.cost_price_cents),
    validityDays: row.validity_days,
    workflowId: row.workflow_id,
    enabled: row.enabled,
    sortOrder: row.sort_order,
    deletedAt: row.deleted_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };

const mapWorkflow = (row) =>
  row && {
    id: row.id,
    network: row.network,
    name: row.name,
    steps: row.steps,
    successPattern: row.success_pattern,
    failurePattern: row.failure_pattern,
    timeoutMs: row.timeout_ms,
    enabled: row.enabled,
    verifiedBy: row.verified_by,
    version: row.version,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };

const mapTransaction = (row) =>
  row && {
    id: row.id,
    reference: row.reference,
    customerMsisdn: row.customer_msisdn,
    network: row.network,
    bundleId: row.bundle_id,
    bundleName: row.bundle_name,
    bundleSizeMb: row.bundle_size_mb,
    amountCents: Number(row.amount_cents),
    paymentStatus: row.payment_status,
    purchaseStatus: row.purchase_status,
    paymentReference: row.payment_reference,
    mpesaReceipt: row.mpesa_receipt,
    checkoutRequestId: row.checkout_request_id,
    failureReason: row.failure_reason,
    simSlot: row.sim_slot,
    manualConfirmationRequired: row.manual_confirmation_required,
    idempotencyKey: row.idempotency_key,
    testMode: row.test_mode,
    deviceId: row.device_id,
    paidAt: row.paid_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };

const mapPayment = (row) =>
  row && {
    id: row.id,
    transactionId: row.transaction_id,
    provider: row.provider,
    checkoutRequestId: row.checkout_request_id,
    merchantRequestId: row.merchant_request_id,
    amountCents: Number(row.amount_cents),
    phone: row.phone,
    status: row.status,
    resultCode: row.result_code,
    resultDesc: row.result_desc,
    mpesaReceipt: row.mpesa_receipt,
    rawRequest: row.raw_request,
    rawCallback: row.raw_callback,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };

const mapJob = (row) =>
  row && {
    id: row.id,
    transactionId: row.transaction_id,
    network: row.network,
    workflowId: row.workflow_id,
    status: row.status,
    attemptCount: row.attempt_count,
    maxAttempts: row.max_attempts,
    nextAttemptAt: row.next_attempt_at,
    claimedBy: row.claimed_by,
    leaseExpiresAt: row.lease_expires_at,
    lastError: row.last_error,
    simSlot: row.sim_slot,
    stepsLog: row.steps_log,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };

/**
 * PostgreSQL repository used in production (Neon).
 * Every method is a single atomic statement where correctness depends on it.
 */
export function createPgRepository(pool) {
  const query = (text, params) => pool.query(text, params);

  return {
    kind: 'postgres',

    admins: {
      async create({ email, name, role = 'operator', passwordHash }) {
        try {
          const { rows } = await query(
            `INSERT INTO admins (email, name, role, password_hash)
             VALUES ($1, $2, $3, $4) RETURNING *`,
            [email.toLowerCase(), name, role, passwordHash]
          );
          return mapAdmin(rows[0]);
        } catch (error) {
          if (error.code === '23505' || error.code === '23514') {
            throw conflict('An administrator with that email already exists', 'email_taken');
          }
          throw error;
        }
      },
      async findByEmail(email) {
        const { rows } = await query('SELECT * FROM admins WHERE lower(email) = lower($1)', [email]);
        return mapAdmin(rows[0]);
      },
      async findById(id) {
        const { rows } = await query('SELECT * FROM admins WHERE id = $1', [id]);
        return mapAdmin(rows[0]);
      },
      async touchLogin(id) {
        await query('UPDATE admins SET last_login_at = now() WHERE id = $1', [id]);
      },
      async count() {
        const { rows } = await query('SELECT count(*)::int AS count FROM admins');
        return rows[0].count;
      }
    },

    devices: {
      async create({ id, label, apiKeyHash }) {
        const deviceId = id ?? crypto.randomUUID();
        const { rows } = await query(
          `INSERT INTO devices (id, label, api_key_hash) VALUES ($1, $2, $3) RETURNING *`,
          [deviceId, label, apiKeyHash]
        );
        return mapDevice(rows[0]);
      },
      async findById(id) {
        const { rows } = await query('SELECT * FROM devices WHERE id = $1', [id]);
        return mapDevice(rows[0]);
      },
      async list() {
        const { rows } = await query(
          'SELECT id, label, enabled, last_seen_at, created_at FROM devices ORDER BY created_at DESC'
        );
        return rows.map((row) =>
          mapDevice({ ...row, api_key_hash: undefined })
        );
      },
      async setEnabled(id, enabled) {
        const { rows } = await query(
          'UPDATE devices SET enabled = $2 WHERE id = $1 RETURNING *',
          [id, enabled]
        );
        return mapDevice(rows[0]);
      },
      async touch(id) {
        await query('UPDATE devices SET last_seen_at = now() WHERE id = $1', [id]);
      }
    },

    bundles: {
      async list({ network, includeDisabled = false } = {}) {
        const conditions = ['deleted_at IS NULL'];
        const params = [];
        if (network) {
          params.push(network);
          conditions.push(`network = $${params.length}`);
        }
        if (!includeDisabled) conditions.push('enabled = TRUE');
        const { rows } = await query(
          `SELECT * FROM bundles WHERE ${conditions.join(' AND ')}
           ORDER BY network, sort_order, data_size_mb`,
          params
        );
        return rows.map(mapBundle);
      },
      async findById(id) {
        const { rows } = await query(
          'SELECT * FROM bundles WHERE id = $1 AND deleted_at IS NULL',
          [id]
        );
        return mapBundle(rows[0]);
      },
      async create(data) {
        const { rows } = await query(
          `INSERT INTO bundles
             (network, name, data_size_mb, selling_price_cents, cost_price_cents,
              validity_days, workflow_id, enabled, sort_order)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
          [
            data.network,
            data.name,
            data.dataSizeMb,
            data.sellingPriceCents,
            data.costPriceCents ?? 0,
            data.validityDays ?? 30,
            data.workflowId ?? null,
            data.enabled ?? true,
            data.sortOrder ?? 0
          ]
        );
        return mapBundle(rows[0]);
      },
      async update(id, patch) {
        const columns = [];
        const params = [id];
        const assign = (column, value) => {
          params.push(value);
          columns.push(`${column} = $${params.length}`);
        };
        if (patch.name !== undefined) assign('name', patch.name);
        if (patch.dataSizeMb !== undefined) assign('data_size_mb', patch.dataSizeMb);
        if (patch.sellingPriceCents !== undefined) assign('selling_price_cents', patch.sellingPriceCents);
        if (patch.costPriceCents !== undefined) assign('cost_price_cents', patch.costPriceCents);
        if (patch.validityDays !== undefined) assign('validity_days', patch.validityDays);
        if (patch.workflowId !== undefined) assign('workflow_id', patch.workflowId);
        if (patch.enabled !== undefined) assign('enabled', patch.enabled);
        if (patch.sortOrder !== undefined) assign('sort_order', patch.sortOrder);
        if (columns.length === 0) return this.findById(id);
        const { rows } = await query(
          `UPDATE bundles SET ${columns.join(', ')} WHERE id = $1 AND deleted_at IS NULL RETURNING *`,
          params
        );
        return mapBundle(rows[0]);
      },
      async softDelete(id) {
        const { rowCount } = await query(
          'UPDATE bundles SET deleted_at = now(), enabled = FALSE WHERE id = $1',
          [id]
        );
        return rowCount > 0;
      }
    },

    workflows: {
      async list({ network } = {}) {
        const params = [];
        let where = '';
        if (network) {
          params.push(network);
          where = 'WHERE network = $1';
        }
        const { rows } = await query(
          `SELECT * FROM ussd_workflows ${where} ORDER BY network, updated_at DESC`,
          params
        );
        return rows.map(mapWorkflow);
      },
      async findById(id) {
        const { rows } = await query('SELECT * FROM ussd_workflows WHERE id = $1', [id]);
        return mapWorkflow(rows[0]);
      },
      async findEnabledByNetwork(network) {
        const { rows } = await query(
          `SELECT * FROM ussd_workflows
           WHERE network = $1 AND enabled = TRUE AND verified_by IS NOT NULL
           ORDER BY updated_at DESC LIMIT 1`,
          [network]
        );
        return mapWorkflow(rows[0]);
      },
      async create(data) {
        const { rows } = await query(
          `INSERT INTO ussd_workflows
             (network, name, steps, success_pattern, failure_pattern, timeout_ms, enabled, verified_by)
           VALUES ($1,$2,$3::jsonb,$4,$5,$6,$7,$8) RETURNING *`,
          [
            data.network,
            data.name,
            JSON.stringify(data.steps ?? []),
            data.successPattern ?? null,
            data.failurePattern ?? null,
            data.timeoutMs ?? 45000,
            data.enabled ?? false,
            data.verifiedBy ?? null
          ]
        );
        return mapWorkflow(rows[0]);
      },
      async update(id, patch) {
        const columns = [];
        const params = [id];
        const assign = (column, value) => {
          params.push(value);
          columns.push(`${column} = $${params.length}`);
        };
        if (patch.name !== undefined) assign('name', patch.name);
        if (patch.steps !== undefined) assign('steps', JSON.stringify(patch.steps));
        if (patch.successPattern !== undefined) assign('success_pattern', patch.successPattern);
        if (patch.failurePattern !== undefined) assign('failure_pattern', patch.failurePattern);
        if (patch.timeoutMs !== undefined) assign('timeout_ms', patch.timeoutMs);
        if (patch.enabled !== undefined) assign('enabled', patch.enabled);
        if (patch.verifiedBy !== undefined) assign('verified_by', patch.verifiedBy);
        if (columns.length === 0) return this.findById(id);
        columns.push('version = version + 1');
        const { rows } = await query(
          `UPDATE ussd_workflows SET ${columns.join(', ')} WHERE id = $1 RETURNING *`,
          params
        );
        return mapWorkflow(rows[0]);
      }
    },

    transactions: {
      async create(data) {
        try {
          const { rows } = await query(
            `INSERT INTO transactions
               (reference, customer_msisdn, network, bundle_id, bundle_name, bundle_size_mb,
                amount_cents, idempotency_key, sim_slot, test_mode, device_id)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *`,
            [
              data.reference,
              data.customerMsisdn,
              data.network,
              data.bundleId ?? null,
              data.bundleName,
              data.bundleSizeMb,
              data.amountCents,
              data.idempotencyKey,
              data.simSlot ?? null,
              data.testMode ?? false,
              data.deviceId ?? null
            ]
          );
          return mapTransaction(rows[0]);
        } catch (error) {
          if (error.code === '23505') {
            throw conflict('Duplicate idempotency key', 'duplicate_idempotency_key');
          }
          throw error;
        }
      },
      async findById(id) {
        const { rows } = await query('SELECT * FROM transactions WHERE id = $1', [id]);
        return mapTransaction(rows[0]);
      },
      async findByIdempotencyKey(key) {
        const { rows } = await query(
          'SELECT * FROM transactions WHERE idempotency_key = $1',
          [key]
        );
        return mapTransaction(rows[0]);
      },
      async findActiveDuplicate({ msisdn, bundleId, since }) {
        const { rows } = await query(
          `SELECT * FROM transactions
           WHERE customer_msisdn = $1
             AND bundle_id = $2
             AND created_at >= $3
             AND (payment_status = 'PENDING'
                  OR purchase_status IN ('QUEUED','RUNNING','AWAITING_MANUAL_CONFIRMATION'))
           ORDER BY created_at DESC LIMIT 1`,
          [msisdn, bundleId, since]
        );
        return mapTransaction(rows[0]);
      },
      async list({ limit = 50, offset = 0, status } = {}) {
        const params = [];
        let where = '';
        if (status) {
          params.push(status);
          where = 'WHERE purchase_status = $1';
        }
        params.push(limit, offset);
        const { rows } = await query(
          `SELECT * FROM transactions ${where}
           ORDER BY created_at DESC
           LIMIT $${params.length - 1} OFFSET $${params.length}`,
          params
        );
        return rows.map(mapTransaction);
      },
      async update(id, patch) {
        const columns = [];
        const params = [id];
        const assign = (column, value) => {
          params.push(value);
          columns.push(`${column} = $${params.length}`);
        };
        if (patch.paymentStatus !== undefined) assign('payment_status', patch.paymentStatus);
        if (patch.purchaseStatus !== undefined) assign('purchase_status', patch.purchaseStatus);
        if (patch.paymentReference !== undefined) assign('payment_reference', patch.paymentReference);
        if (patch.mpesaReceipt !== undefined) assign('mpesa_receipt', patch.mpesaReceipt);
        if (patch.checkoutRequestId !== undefined) assign('checkout_request_id', patch.checkoutRequestId);
        if (patch.failureReason !== undefined) assign('failure_reason', patch.failureReason);
        if (patch.manualConfirmationRequired !== undefined) {
          assign('manual_confirmation_required', patch.manualConfirmationRequired);
        }
        if (patch.paidAt !== undefined) assign('paid_at', patch.paidAt);
        if (columns.length === 0) return this.findById(id);
        const { rows } = await query(
          `UPDATE transactions SET ${columns.join(', ')} WHERE id = $1 RETURNING *`,
          params
        );
        return mapTransaction(rows[0]);
      },
      async summary() {
        const { rows } = await query(
          `SELECT
             COALESCE(SUM(amount_cents) FILTER (WHERE payment_status = 'PAID'), 0)::bigint AS total_sales,
             COALESCE(SUM(amount_cents) FILTER (WHERE payment_status = 'PAID' AND created_at >= date_trunc('day', now())), 0)::bigint AS today_revenue,
             COUNT(*)::int AS total,
             COUNT(*) FILTER (WHERE purchase_status = 'SUCCESS')::int AS successful,
             COUNT(*) FILTER (WHERE purchase_status = 'FAILED')::int AS failed,
             COUNT(*) FILTER (WHERE payment_status = 'PENDING')::int AS pending
           FROM transactions`
        );
        const row = rows[0];
        return {
          totalSalesCents: Number(row.total_sales),
          todayRevenueCents: Number(row.today_revenue),
          totalTransactions: row.total,
          successfulTransactions: row.successful,
          failedTransactions: row.failed,
          pendingTransactions: row.pending,
          activeBundles: 0
        };
      }
    },

    payments: {
      async create(data) {
        const { rows } = await query(
          `INSERT INTO payments
             (transaction_id, provider, checkout_request_id, merchant_request_id, amount_cents, phone, status, raw_request)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb) RETURNING *`,
          [
            data.transactionId,
            data.provider ?? 'MPESA',
            data.checkoutRequestId ?? null,
            data.merchantRequestId ?? null,
            data.amountCents,
            data.phone,
            data.status ?? 'INITIATED',
            data.rawRequest ? JSON.stringify(data.rawRequest) : null
          ]
        );
        return mapPayment(rows[0]);
      },
      async findByCheckoutId(checkoutRequestId) {
        const { rows } = await query(
          'SELECT * FROM payments WHERE checkout_request_id = $1',
          [checkoutRequestId]
        );
        return mapPayment(rows[0]);
      },
      async update(id, patch) {
        const columns = [];
        const params = [id];
        const assign = (column, value) => {
          params.push(value);
          columns.push(`${column} = $${params.length}`);
        };
        if (patch.status !== undefined) assign('status', patch.status);
        if (patch.resultCode !== undefined) assign('result_code', patch.resultCode);
        if (patch.resultDesc !== undefined) assign('result_desc', patch.resultDesc);
        if (patch.mpesaReceipt !== undefined) assign('mpesa_receipt', patch.mpesaReceipt);
        if (patch.rawCallback !== undefined) {
          params.push(JSON.stringify(patch.rawCallback));
          columns.push(`raw_callback = $${params.length}::jsonb`);
        }
        if (columns.length === 0) return null;
        const { rows } = await query(
          `UPDATE payments SET ${columns.join(', ')} WHERE id = $1 RETURNING *`,
          params
        );
        return mapPayment(rows[0]);
      },
      async listByTransaction(transactionId) {
        const { rows } = await query(
          'SELECT * FROM payments WHERE transaction_id = $1 ORDER BY created_at DESC',
          [transactionId]
        );
        return rows.map(mapPayment);
      }
    },

    ussdJobs: {
      async create(data) {
        try {
          const { rows } = await query(
            `INSERT INTO ussd_jobs (transaction_id, network, workflow_id, status, max_attempts, sim_slot)
             VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
            [
              data.transactionId,
              data.network,
              data.workflowId ?? null,
              data.status ?? 'QUEUED',
              data.maxAttempts ?? 3,
              data.simSlot ?? 0
            ]
          );
          return mapJob(rows[0]);
        } catch (error) {
          if (error.code === '23505') {
            throw conflict('A delivery job already exists for this transaction', 'job_exists');
          }
          if (error.code === '23514') {
            throw conflict(error.message, 'job_requires_paid_transaction');
          }
          throw error;
        }
      },
      /**
       * Atomically leases one due job. `FOR UPDATE SKIP LOCKED` means two terminals
       * polling at the same time can never claim the same delivery.
       */
      async claimNext({ deviceId, leaseSeconds = 120 }) {
        const { rows } = await query(
          `WITH candidate AS (
             SELECT id FROM ussd_jobs
             WHERE status IN ('QUEUED','AWAITING_MANUAL_CONFIRMATION')
               AND next_attempt_at <= now()
             ORDER BY created_at
             FOR UPDATE SKIP LOCKED
             LIMIT 1
           )
           UPDATE ussd_jobs j
           SET status = 'RUNNING',
               attempt_count = j.attempt_count + 1,
               claimed_by = $1,
               lease_expires_at = now() + ($2 || ' seconds')::interval
           FROM candidate
           WHERE j.id = candidate.id
           RETURNING j.*`,
          [deviceId ?? null, String(leaseSeconds)]
        );
        return mapJob(rows[0]);
      },
      async findById(id) {
        const { rows } = await query('SELECT * FROM ussd_jobs WHERE id = $1', [id]);
        return mapJob(rows[0]);
      },
      async findByTransaction(transactionId) {
        const { rows } = await query(
          'SELECT * FROM ussd_jobs WHERE transaction_id = $1',
          [transactionId]
        );
        return mapJob(rows[0]);
      },
      async update(id, patch) {
        const columns = [];
        const params = [id];
        const assign = (column, value) => {
          params.push(value);
          columns.push(`${column} = $${params.length}`);
        };
        if (patch.status !== undefined) assign('status', patch.status);
        if (patch.lastError !== undefined) assign('last_error', patch.lastError);
        if (patch.nextAttemptAt !== undefined) assign('next_attempt_at', patch.nextAttemptAt);
        if (patch.stepsLog !== undefined) {
          params.push(JSON.stringify(patch.stepsLog));
          columns.push(`steps_log = $${params.length}::jsonb`);
        }
        if (patch.attemptCount !== undefined) assign('attempt_count', patch.attemptCount);
        if (patch.leaseExpiresAt !== undefined) assign('lease_expires_at', patch.leaseExpiresAt);
        if (patch.claimedBy !== undefined) assign('claimed_by', patch.claimedBy);
        if (columns.length === 0) return this.findById(id);
        const { rows } = await query(
          `UPDATE ussd_jobs SET ${columns.join(', ')} WHERE id = $1 RETURNING *`,
          params
        );
        return mapJob(rows[0]);
      },
      async list({ status, limit = 50 } = {}) {
        const params = [];
        let where = '';
        if (status) {
          params.push(status);
          where = 'WHERE status = $1';
        }
        params.push(limit);
        const { rows } = await query(
          `SELECT * FROM ussd_jobs ${where} ORDER BY created_at DESC LIMIT $${params.length}`,
          params
        );
        return rows.map(mapJob);
      }
    },

    audit: {
      async record(entry) {
        await query(
          `INSERT INTO audit_logs (actor_type, actor_id, action, target, detail, ip)
           VALUES ($1,$2,$3,$4,$5::jsonb,$6)`,
          [
            entry.actorType ?? 'system',
            entry.actorId ?? null,
            entry.action,
            entry.target ?? null,
            entry.detail ? JSON.stringify(entry.detail) : null,
            entry.ip ?? null
          ]
        );
      },
      async list({ limit = 100 } = {}) {
        const { rows } = await query(
          'SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT $1',
          [limit]
        );
        return rows.map((row) => ({
          id: row.id,
          actorType: row.actor_type,
          actorId: row.actor_id,
          action: row.action,
          target: row.target,
          detail: row.detail,
          ip: row.ip,
          createdAt: row.created_at
        }));
      }
    }
  };
}
