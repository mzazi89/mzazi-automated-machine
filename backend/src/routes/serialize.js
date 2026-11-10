/** API serialisers — nothing internal (hashes, keys) ever leaves through here. */

export function toMillis(value) {
  if (value === null || value === undefined) return null;
  if (typeof value === 'number') return value;
  const parsed = new Date(value).getTime();
  return Number.isNaN(parsed) ? null : parsed;
}

const iso = (value) => (value ? new Date(value).toISOString() : null);

export function transactionDto(t) {
  return {
    id: t.id,
    reference: t.reference,
    customerMsisdn: t.customerMsisdn,
    network: t.network,
    bundleId: t.bundleId ?? null,
    bundleName: t.bundleName,
    bundleSizeMb: t.bundleSizeMb,
    amountCents: Number(t.amountCents),
    paymentStatus: t.paymentStatus,
    purchaseStatus: t.purchaseStatus,
    paymentReference: t.paymentReference ?? null,
    mpesaReceipt: t.mpesaReceipt ?? null,
    checkoutRequestId: t.checkoutRequestId ?? null,
    failureReason: t.failureReason ?? null,
    simSlot: t.simSlot ?? null,
    manualConfirmationRequired: Boolean(t.manualConfirmationRequired),
    testMode: Boolean(t.testMode),
    sellerId: t.sellerId ?? null,
    // Android reads createdAt/updatedAt as epoch millis.
    createdAt: toMillis(t.createdAt),
    updatedAt: toMillis(t.updatedAt),
    createdAtIso: iso(t.createdAt),
    paidAt: iso(t.paidAt),
    idempotencyKey: t.idempotencyKey
  };
}

export function bundleDto(b) {
  return {
    id: b.id,
    network: b.network,
    name: b.name,
    dataSizeMb: b.dataSizeMb,
    sellingPriceCents: Number(b.sellingPriceCents),
    costPriceCents: Number(b.costPriceCents ?? 0),
    validityDays: b.validityDays,
    workflowId: b.workflowId ?? null,
    enabled: Boolean(b.enabled),
    sortOrder: b.sortOrder ?? 0,
    updatedAt: toMillis(b.updatedAt ?? b.createdAt) ?? Date.now()
  };
}

export function workflowDto(w) {
  return {
    id: w.id,
    network: w.network,
    name: w.name,
    steps: Array.isArray(w.steps) ? w.steps : [],
    successPattern: w.successPattern ?? null,
    failurePattern: w.failurePattern ?? null,
    timeoutMs: w.timeoutMs ?? 45000,
    enabled: Boolean(w.enabled),
    verifiedBy: w.verifiedBy ?? null,
    version: w.version ?? 1,
    updatedAt: toMillis(w.updatedAt ?? w.createdAt) ?? Date.now()
  };
}

export function deviceDto(d) {
  return {
    id: d.id,
    label: d.label,
    enabled: Boolean(d.enabled),
    lastSeenAt: iso(d.lastSeenAt),
    createdAt: iso(d.createdAt)
  };
}

export function jobDto(job) {
  return {
    id: job.id,
    transactionId: job.transactionId,
    network: job.network,
    workflowId: job.workflowId ?? null,
    status: job.status,
    attemptCount: job.attemptCount,
    maxAttempts: job.maxAttempts,
    simSlot: job.simSlot ?? 0,
    lastError: job.lastError ?? null,
    stepsLog: job.stepsLog ?? [],
    createdAt: toMillis(job.createdAt),
    updatedAt: toMillis(job.updatedAt)
  };
}

export function auditDto(entry) {
  return {
    id: String(entry.id),
    actorType: entry.actorType,
    actorId: entry.actorId ?? null,
    action: entry.action,
    target: entry.target ?? null,
    detail: entry.detail ?? null,
    ip: entry.ip ?? null,
    createdAt: iso(entry.createdAt)
  };
}
