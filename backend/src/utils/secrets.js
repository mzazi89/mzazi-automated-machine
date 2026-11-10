import crypto from 'node:crypto';

/**
 * Authenticated encryption for third-party secrets that must be replayable
 * (a Daraja passkey has to be sent back to Safaricom, so hashing is not an option).
 *
 * AES-256-GCM with a random 12-byte IV per value; the IV and auth tag are stored
 * alongside the ciphertext. Key material comes from TILL_ENCRYPTION_KEY and never
 * touches the database or the repository.
 */
const ALGORITHM = 'aes-256-gcm';

function decodeKey(raw) {
  if (!raw) return null;
  const buffer = /^[0-9a-f]{64}$/i.test(raw)
    ? Buffer.from(raw, 'hex')
    : Buffer.from(raw, 'base64');
  return buffer.length === 32 ? buffer : null;
}

export function encryptionAvailable(key) {
  return decodeKey(key) !== null;
}

export function encryptSecret(plaintext, key) {
  const keyBuffer = decodeKey(key);
  if (!keyBuffer) {
    throw new Error('TILL_ENCRYPTION_KEY must be 32 bytes (base64 or 64 hex characters)');
  }
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv(ALGORITHM, keyBuffer, iv);
  const ciphertext = Buffer.concat([cipher.update(String(plaintext), 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, ciphertext]).toString('base64');
}

export function decryptSecret(payload, key) {
  const keyBuffer = decodeKey(key);
  if (!keyBuffer) throw new Error('TILL_ENCRYPTION_KEY is missing or malformed');
  const raw = Buffer.from(String(payload), 'base64');
  const iv = raw.subarray(0, 12);
  const tag = raw.subarray(12, 28);
  const ciphertext = raw.subarray(28);
  const decipher = crypto.createDecipheriv(ALGORITHM, keyBuffer, iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}

/** Only ever expose the tail of a secret to the owner UI. */
export function maskSecret(plaintext) {
  if (!plaintext) return null;
  const value = String(plaintext);
  return value.length <= 4 ? '••••' : `••••${value.slice(-4)}`;
}

export function generateEncryptionKey() {
  return crypto.randomBytes(32).toString('base64');
}
