/** All amounts are stored and transported as integer cents (KES * 100). */
export function toCents(value) {
  const amount = typeof value === 'string' ? Number.parseFloat(value.replace(/,/g, '')) : Number(value);
  if (!Number.isFinite(amount) || amount < 0) return null;
  return Math.round(amount * 100);
}

export function toShillings(cents) {
  return Math.round(Number(cents) / 100);
}

export function formatKes(cents) {
  return new Intl.NumberFormat('en-KE', {
    style: 'currency',
    currency: 'KES',
    minimumFractionDigits: 2
  }).format(Number(cents) / 100);
}

/** Daraja requires a whole-shilling integer amount. */
export function toDarajaAmount(cents) {
  if (!Number.isInteger(Number(cents)) || Number(cents) % 100 !== 0) {
    // Silently rounding 100.50 up to 101 would overcharge the customer.
    throw new Error('M-Pesa amount must be a whole number of shillings');
  }
  const shillings = Number(cents) / 100;
  if (shillings < 1) throw new Error('M-Pesa amount must be at least 1 shilling');
  return shillings;
}
