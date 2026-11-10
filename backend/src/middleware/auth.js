import { forbidden, unauthorized } from '../utils/errors.js';
import { sha256, timingSafeEquals } from '../utils/crypto.js';

/**
 * Two independent principals use this API:
 *  - administrators (Bearer JWT)
 *  - Android terminals (X-Device-Id + X-Device-Key)
 * Device keys are stored only as SHA-256 hashes.
 */
export function createAuthMiddleware({ authService, repo }) {
  function requireAdmin(req, _res, next) {
    const header = req.headers.authorization ?? '';
    if (!header.startsWith('Bearer ')) {
      return next(unauthorized('Missing bearer token', 'missing_token'));
    }
    try {
      const payload = authService.verifyToken(header.slice(7), 'access');
      // Tokens minted before the owner/seller split keep working for their 12h life.
      const role =
        payload.role === 'seller' || payload.role === 'operator' ? 'seller' : 'owner';
      req.admin = { id: payload.sub, email: payload.email, role };
      return next();
    } catch (error) {
      return next(error);
    }
  }

  function requireRole(...roles) {
    return (req, _res, next) => {
      if (!req.admin) return next(unauthorized());
      if (!roles.includes(req.admin.role)) {
        return next(forbidden('This action requires a higher privilege level', 'insufficient_role'));
      }
      return next();
    };
  }

  async function requireDevice(req, _res, next) {
    try {
      const deviceId = req.headers['x-device-id'];
      const deviceKey = req.headers['x-device-key'];
      if (!deviceId || !deviceKey) {
        throw unauthorized('Device credentials are required', 'missing_device_credentials');
      }
      const device = await repo.devices.findById(String(deviceId));
      if (!device || !device.enabled) {
        throw unauthorized('Unknown or disabled device', 'invalid_device');
      }
      if (!timingSafeEquals(sha256(String(deviceKey)), device.apiKeyHash)) {
        throw unauthorized('Invalid device key', 'invalid_device_key');
      }
      await repo.devices.touch(device.id);
      req.device = device;
      return next();
    } catch (error) {
      return next(error);
    }
  }

  /** Accepts either an administrator token or valid device credentials. */
  async function requireAdminOrDevice(req, res, next) {
    if ((req.headers.authorization ?? '').startsWith('Bearer ')) {
      return requireAdmin(req, res, next);
    }
    return requireDevice(req, res, next);
  }

  /** Owner-only guard used by configuration, sellers and till endpoints. */
  const requireOwner = requireRole('owner');

  return { requireAdmin, requireRole, requireOwner, requireDevice, requireAdminOrDevice };
}
