import { Router } from 'express';
import { asyncHandler } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { deviceCreateSchema } from './schemas.js';
import { auditDto, deviceDto } from './serialize.js';
import { randomToken, sha256 } from '../utils/crypto.js';

export function adminRoutes({ services, middleware, repo, config }) {
  const router = Router();

  router.get(
    '/dashboard/summary',
    middleware.requireAdminOrDevice,
    asyncHandler(async (_req, res) => {
      const summary = await services.transactions.summary();
      res.json({
        totalSalesCents: summary.totalSalesCents,
        todayRevenueCents: summary.todayRevenueCents,
        totalTransactions: summary.totalTransactions,
        successfulTransactions: summary.successfulTransactions,
        failedTransactions: summary.failedTransactions,
        pendingTransactions: summary.pendingTransactions,
        activeBundles: summary.activeBundles
      });
    })
  );

  router.get(
    '/admin/devices',
    middleware.requireAdmin,
    asyncHandler(async (_req, res) => {
      res.json((await repo.devices.list()).map(deviceDto));
    })
  );

  /**
   * Issues a device credential. The secret key is returned exactly once and only its
   * SHA-256 hash is stored, so it cannot be recovered later.
   */
  router.post(
    '/admin/devices',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    validateBody(deviceCreateSchema),
    asyncHandler(async (req, res) => {
      const deviceKey = randomToken(32);
      const device = await repo.devices.create({
        label: req.validated.label,
        apiKeyHash: sha256(deviceKey)
      });
      await services.audit.record({
        actorType: 'admin',
        actorId: req.admin.id,
        action: 'device.created',
        target: device.id,
        detail: { label: device.label }
      });
      res.status(201).json({
        ...deviceDto(device),
        deviceId: device.id,
        deviceKey,
        warning: 'Store this key on the terminal now. It cannot be shown again.'
      });
    })
  );

  router.post(
    '/admin/devices/:id/revoke',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    asyncHandler(async (req, res) => {
      const device = await repo.devices.setEnabled(req.params.id, false);
      await services.audit.record({
        actorType: 'admin',
        actorId: req.admin.id,
        action: 'device.revoked',
        target: req.params.id
      });
      res.json(deviceDto(device));
    })
  );

  router.get(
    '/admin/audit-logs',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    asyncHandler(async (req, res) => {
      const limit = Math.min(Number(req.query.limit ?? 100), 500);
      const entries = await repo.audit.list({ limit });
      res.json(entries.map(auditDto));
    })
  );

  router.get(
    '/admin/config',
    middleware.requireAdmin,
    asyncHandler(async (_req, res) => {
      // Never returns secrets — only whether they exist.
      res.json({
        environment: config.env,
        testMode: config.testMode,
        mpesaConfigured: Boolean(
          config.mpesa.consumerKey && config.mpesa.consumerSecret && config.mpesa.passkey
        ),
        duplicateWindowMinutes: config.duplicateWindowMinutes,
        publicBaseUrl: config.publicBaseUrl
      });
    })
  );

  return router;
}
