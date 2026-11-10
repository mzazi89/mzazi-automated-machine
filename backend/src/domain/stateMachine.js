import { conflict } from '../utils/errors.js';

/**
 * Explicit state machines.
 *
 * Payment and fulfilment are deliberately separate: money can be PAID while delivery
 * is still QUEUED, and a failed delivery must never rewrite the payment record.
 */
export const PAYMENT_TRANSITIONS = Object.freeze({
  PENDING: ['PAID', 'FAILED', 'CANCELLED'],
  PAID: ['REVERSED', 'REFUNDED'],
  FAILED: [], // terminal: a new transaction is required, never a silent retry
  CANCELLED: [],
  REVERSED: [],
  REFUNDED: []
});

export const PURCHASE_TRANSITIONS = Object.freeze({
  QUEUED: ['RUNNING', 'CANCELLED', 'FAILED'],
  RUNNING: ['SUCCESS', 'FAILED', 'AWAITING_MANUAL_CONFIRMATION'],
  AWAITING_MANUAL_CONFIRMATION: ['SUCCESS', 'FAILED', 'CANCELLED'],
  SUCCESS: [],
  FAILED: [],
  CANCELLED: []
});

export function canTransition(map, from, to) {
  const allowed = map[from];
  if (!allowed) return false;
  return allowed.includes(to);
}

export function assertTransition(map, from, to, label = 'state') {
  if (from === to) return true;
  if (!canTransition(map, from, to)) {
    throw conflict(`Illegal ${label} transition: ${from} → ${to}`, 'illegal_transition');
  }
  return true;
}

export const isPaymentFinal = (status) =>
  ['PAID', 'FAILED', 'CANCELLED', 'REVERSED', 'REFUNDED'].includes(status);

export const isPurchaseFinal = (status) => ['SUCCESS', 'FAILED', 'CANCELLED'].includes(status);

/**
 * A USSD purchase is not idempotent at the carrier. This decides whether a failed
 * attempt may ever be retried: only when the request provably never reached the
 * operator's application layer.
 */
export function mayRetryDelivery({ status, attemptCount, maxAttempts, failureCategory }) {
  if (status !== 'QUEUED') return false;
  if (attemptCount >= maxAttempts) return false;
  return failureCategory === 'NETWORK_ERROR';
}
