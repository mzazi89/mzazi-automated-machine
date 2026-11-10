import { badRequest, notFound, unprocessable } from '../utils/errors.js';
import { decryptSecret, encryptSecret, encryptionAvailable } from '../utils/secrets.js';

const NETWORKS = new Set(['SAFARICOM', 'AIRTEL', 'TELKOM']);

/**
 * Tills (M-Pesa shortcodes) the owner connects per network.
 *
 * The passkey is encrypted at rest with TILL_ENCRYPTION_KEY and is never returned by
 * the API — only a masked hint, so the owner can recognise which passkey is loaded.
 */
export function createTillService({ repo, config, audit }) {
  function keyOrFail() {
    if (!encryptionAvailable(config.tillEncryptionKey)) {
      throw unprocessable(
        'TILL_ENCRYPTION_KEY is not configured on the server, so till passkeys cannot be stored safely.',
        'encryption_key_missing'
      );
    }
    return config.tillEncryptionKey;
  }

  function toDto(till) {
    let hint = null;
    if (till.passkeyCipher) {
      try {
        const plain = decryptSecret(till.passkeyCipher, config.tillEncryptionKey);
        hint = `••••${plain.slice(-4)}`;
      } catch {
        hint = '••••';
      }
    }
    return {
      id: till.id,
      network: till.network,
      label: till.label,
      shortcode: till.shortcode,
      accountType: till.accountType ?? 'TILL',
      accountNumber: till.accountNumber ?? null,
      isDefault: till.isDefault,
      enabled: till.enabled,
      hasPasskey: Boolean(till.passkeyCipher),
      passkeyHint: hint,
      createdAt: till.createdAt,
      updatedAt: till.updatedAt
    };
  }

  async function list({ network, includeDisabled = true } = {}) {
    const tills = await repo.tills.list({ network, includeDisabled });
    return tills.map(toDto);
  }

  async function create(payload, actor) {
    const network = String(payload.network ?? '').toUpperCase();
    if (!NETWORKS.has(network)) throw unprocessable('network must be SAFARICOM, AIRTEL or TELKOM', 'invalid_network');

    const label = String(payload.label ?? '').trim();
    if (label.length < 2) throw badRequest('Give the till a label (for example "Safaricom Till")', 'invalid_label');

    const shortcode = String(payload.shortcode ?? '').trim();
    if (!/^[0-9]{5,8}$/.test(shortcode)) {
      throw badRequest('shortcode must be 5–8 digits', 'invalid_shortcode');
    }

    let passkeyCipher = null;
    if (payload.passkey) {
      passkeyCipher = encryptSecret(String(payload.passkey).trim(), keyOrFail());
    }

    const created = await repo.tills.create({
      network,
      label,
      shortcode,
      accountType: payload.accountType ?? 'TILL',
      accountNumber: payload.accountNumber ?? null,
      passkeyCipher,
      isDefault: payload.isDefault ?? false,
      createdBy: actor?.id ?? null
    });

    await audit?.record({
      actorType: 'admin',
      actorId: actor?.id ?? null,
      action: 'till.created',
      target: created.id,
      detail: { network, shortcode, accountType: created.accountType }
    });

    return toDto(created);
  }

  async function update(id, payload, actor) {
    const existing = await repo.tills.findById(id);
    if (!existing) throw notFound('Till not found', 'till_not_found');

    const patch = {};
    if (payload.label !== undefined) patch.label = String(payload.label).trim();
    if (payload.accountNumber !== undefined) patch.accountNumber = payload.accountNumber;
    if (payload.accountType !== undefined) patch.accountType = payload.accountType;
    if (payload.enabled !== undefined) patch.enabled = Boolean(payload.enabled);
    if (payload.isDefault !== undefined) patch.isDefault = Boolean(payload.isDefault);
    if (payload.shortcode !== undefined) {
      if (!/^[0-9]{5,8}$/.test(String(payload.shortcode))) {
        throw badRequest('shortcode must be 5–8 digits', 'invalid_shortcode');
      }
      patch.shortcode = String(payload.shortcode);
    }
    if (payload.passkey) {
      patch.passkeyCipher = encryptSecret(String(payload.passkey).trim(), keyOrFail());
    }

    const updated = await repo.tills.update(id, patch);
    await audit?.record({
      actorType: 'admin',
      actorId: actor?.id ?? null,
      action: 'till.updated',
      target: id,
      detail: { fields: Object.keys(patch) }
    });
    return toDto(updated);
  }

  /** Credentials used for an STK push, or null to fall back to the env bootstrap till. */
  async function credentialsForNetwork(network) {
    const till = await repo.tills.findDefault(network);
    if (!till || !till.passkeyCipher) return null;
    if (!encryptionAvailable(config.tillEncryptionKey)) return null;
    return {
      shortcode: till.shortcode,
      passkey: decryptSecret(till.passkeyCipher, config.tillEncryptionKey),
      tillId: till.id
    };
  }

  return { list, create, update, credentialsForNetwork, toDto };
}
