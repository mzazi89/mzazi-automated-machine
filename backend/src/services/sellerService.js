import { badRequest, conflict, notFound, unprocessable } from '../utils/errors.js';
import { hashPassword } from '../utils/crypto.js';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Seller accounts.
 *
 * Two invariants matter:
 *  - the API can only ever create `seller` accounts (owners come from the seed or
 *    another owner), so privilege cannot be escalated through the user endpoints;
 *  - the last active owner can never be disabled, which would lock everyone out.
 */
export function createSellerService({ repo, audit }) {
  function toDto(admin) {
    return {
      id: admin.id,
      email: admin.email,
      name: admin.name,
      role: admin.role,
      disabled: Boolean(admin.disabled),
      lastLoginAt: admin.lastLoginAt ?? null,
      createdAt: admin.createdAt ?? null,
      createdBy: admin.createdBy ?? null
    };
  }

  async function list({ role } = {}) {
    const admins = await repo.admins.list({ role });
    return admins.map(toDto);
  }

  async function create({ email, name, password }, actor) {
    const normalizedEmail = String(email ?? '').trim().toLowerCase();
    if (!EMAIL_RE.test(normalizedEmail)) throw badRequest('Enter a valid email address', 'invalid_email');

    const cleanName = String(name ?? '').trim();
    if (cleanName.length < 2) throw badRequest('Enter the seller name', 'invalid_name');
    if (typeof password !== 'string' || password.length < 8) {
      throw badRequest('Password must be at least 8 characters', 'weak_password');
    }

    if (await repo.admins.findByEmail(normalizedEmail)) {
      throw conflict('An account with that email already exists', 'email_taken');
    }

    const created = await repo.admins.create({
      email: normalizedEmail,
      name: cleanName,
      role: 'seller', // never taken from the request body
      passwordHash: await hashPassword(password),
      createdBy: actor?.id ?? null
    });

    await audit?.record({
      actorType: 'admin',
      actorId: actor?.id ?? null,
      action: 'seller.created',
      target: created.id,
      detail: { email: normalizedEmail }
    });

    return toDto(created);
  }

  async function update(id, payload, actor) {
    const existing = await repo.admins.findById(id);
    if (!existing) throw notFound('Account not found', 'admin_not_found');

    const patch = {};
    if (payload.name !== undefined) patch.name = String(payload.name).trim();
    if (payload.password !== undefined) {
      if (String(payload.password).length < 8) {
        throw badRequest('Password must be at least 8 characters', 'weak_password');
      }
      patch.passwordHash = await hashPassword(String(payload.password));
    }
    if (payload.disabled !== undefined) {
      const disabling = Boolean(payload.disabled);
      if (disabling && existing.role === 'owner') {
        const owners = (await repo.admins.list({ role: 'owner' })).filter((o) => !o.disabled);
        if (owners.length <= 1) {
          throw unprocessable('The last active owner cannot be disabled', 'last_owner');
        }
      }
      if (disabling && existing.id === actor?.id) {
        throw unprocessable('You cannot disable your own account', 'self_disable');
      }
      patch.disabled = disabling;
    }

    const updated = await repo.admins.update(id, patch);
    await audit?.record({
      actorType: 'admin',
      actorId: actor?.id ?? null,
      action: 'seller.updated',
      target: id,
      detail: { fields: Object.keys(patch) }
    });
    return toDto(updated);
  }

  return { list, create, update };
}
