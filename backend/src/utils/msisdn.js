export const COUNTRY_CODE = '254';

/**
 * Normalises a Kenyan mobile number to 254XXXXXXXXX.
 * Returns null when the input cannot be a valid Kenyan mobile number.
 */
export function normalizeMsisdn(raw) {
  if (typeof raw !== 'string' && typeof raw !== 'number') return null;
  let digits = String(raw).replace(/\D/g, '');
  if (!digits) return null;

  if (digits.startsWith(COUNTRY_CODE)) {
    // keep as is
  } else if (digits.startsWith('0')) {
    digits = COUNTRY_CODE + digits.slice(1);
  } else if (digits.length === 9 && (digits.startsWith('7') || digits.startsWith('1'))) {
    digits = COUNTRY_CODE + digits;
  } else {
    return null;
  }

  return isValidMsisdn(digits) ? digits : null;
}

export function isValidMsisdn(msisdn) {
  if (typeof msisdn !== 'string' || msisdn.length !== 12) return false;
  if (!msisdn.startsWith(COUNTRY_CODE)) return false;
  const subscriber = msisdn.slice(COUNTRY_CODE.length);
  if (!/^\d{9}$/.test(subscriber)) return false;
  return subscriber.startsWith('7') || subscriber.startsWith('1');
}

/** Daraja expects the MSISDN form 2547XXXXXXXX. */
export function toDarajaMsisdn(msisdn) {
  const normalized = normalizeMsisdn(msisdn);
  if (!normalized) throw new Error('Invalid MSISDN for Daraja');
  return normalized;
}

export function maskMsisdn(msisdn) {
  if (typeof msisdn !== 'string' || msisdn.length < 6) return '—';
  return `${msisdn.slice(0, 6)}***${msisdn.slice(-2)}`;
}
