import jwt from 'jsonwebtoken';
import { unauthorized, forbidden } from '../utils/errors.js';
import { verifyPassword } from '../utils/crypto.js';

/**
 * Administrator authentication.
 *
 * Passwords are verified against a scrypt hash; the API only ever returns a signed
 * JWT. The token carries a type claim so an access token can never be used to refresh.
 */
export function createAuthService({ repo, config, audit }) {
  function sign(admin, type) {
    const ttl = type === 'refresh' ? config.jwtRefreshTtl : config.jwtAccessTtl;
    return jwt.sign(
      { sub: admin.id, email: admin.email, role: admin.role, type },
      config.jwtSecret,
      { expiresIn: ttl, issuer: 'mzazi-automated-machine' }
    );
  }

  function issueTokens(admin) {
    return {
      accessToken: sign(admin, 'access'),
      refreshToken: sign(admin, 'refresh'),
      tokenType: 'Bearer',
      expiresIn: config.jwtAccessTtl,
      admin: {
        id: admin.id,
        email: admin.email,
        name: admin.name,
        role: admin.role
      }
    };
  }

  async function login({ email, password, ip }) {
    if (!config.jwtSecret) {
      throw unauthorized('Server is not configured for authentication (JWT_SECRET missing)');
    }
    const admin = await repo.admins.findByEmail(String(email ?? '').trim().toLowerCase());

    // Constant-ish response: never reveal whether the email exists.
    const passwordOk = admin ? await verifyPassword(password ?? '', admin.passwordHash) : false;
    if (!admin || !passwordOk) {
      await audit?.record({
        actorType: 'admin',
        actorId: admin?.id ?? null,
        action: 'auth.login_failed',
        target: String(email ?? '').slice(0, 120),
        ip
      });
      throw unauthorized('Invalid email or password', 'invalid_credentials');
    }
    if (admin.disabled) {
      throw forbidden('This administrator account is disabled', 'account_disabled');
    }

    await repo.admins.touchLogin(admin.id);
    await audit?.record({
      actorType: 'admin',
      actorId: admin.id,
      action: 'auth.login',
      target: admin.email,
      ip
    });
    return issueTokens(admin);
  }

  function verifyToken(token, expectedType = 'access') {
    try {
      const payload = jwt.verify(token, config.jwtSecret, {
        issuer: 'mzazi-automated-machine'
      });
      if (payload.type !== expectedType) throw new Error('wrong token type');
      return payload;
    } catch {
      throw unauthorized('Session is invalid or expired', 'invalid_token');
    }
  }

  async function refresh(refreshToken) {
    const payload = verifyToken(refreshToken, 'refresh');
    const admin = await repo.admins.findById(payload.sub);
    if (!admin || admin.disabled) throw unauthorized('Account is not available', 'invalid_token');
    return issueTokens(admin);
  }

  async function me(adminId) {
    const admin = await repo.admins.findById(adminId);
    if (!admin) throw unauthorized('Account not found', 'invalid_token');
    return { id: admin.id, email: admin.email, name: admin.name, role: admin.role };
  }

  return { login, verifyToken, refresh, me, issueTokens };
}
