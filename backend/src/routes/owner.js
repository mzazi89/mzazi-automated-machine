import { Router } from 'express';
import { asyncHandler } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { sellerCreateSchema, sellerUpdateSchema, tillCreateSchema, tillUpdateSchema } from './schemas.js';

/**
 * Owner-only configuration surface.
 *
 * Every route here is guarded twice: `requireAdmin` proves who is calling, and
 * `requireOwner` proves they may configure the machine. Sellers receive 403 rather
 * than a filtered view, so a mis-scoped client fails loudly instead of leaking data.
 */
export function ownerRoutes({ services, middleware, repo }) {
  const router = Router();
  const ownerOnly = [middleware.requireAdmin, middleware.requireOwner];

  // ---- Sellers -----------------------------------------------------------
  router.get(
    '/admin/sellers',
    ownerOnly,
    asyncHandler(async (req, res) => {
      const sellers = await services.sellers.list({ role: req.query.role });
      res.json({ sellers });
    })
  );

  router.post(
    '/admin/sellers',
    ownerOnly,
    validateBody(sellerCreateSchema),
    asyncHandler(async (req, res) => {
      const seller = await services.sellers.create(req.body, req.admin);
      res.status(201).json({ seller });
    })
  );

  router.patch(
    '/admin/sellers/:id',
    ownerOnly,
    validateBody(sellerUpdateSchema),
    asyncHandler(async (req, res) => {
      const seller = await services.sellers.update(req.params.id, req.body, req.admin);
      res.json({ seller });
    })
  );

  // ---- Tills -------------------------------------------------------------
  router.get(
    '/admin/tills',
    ownerOnly,
    asyncHandler(async (req, res) => {
      const tills = await services.tills.list({ network: req.query.network });
      res.json({ tills });
    })
  );

  router.post(
    '/admin/tills',
    ownerOnly,
    validateBody(tillCreateSchema),
    asyncHandler(async (req, res) => {
      const till = await services.tills.create(req.body, req.admin);
      res.status(201).json({ till });
    })
  );

  router.patch(
    '/admin/tills/:id',
    ownerOnly,
    validateBody(tillUpdateSchema),
    asyncHandler(async (req, res) => {
      const till = await services.tills.update(req.params.id, req.body, req.admin);
      res.json({ till });
    })
  );

  router.delete(
    '/admin/tills/:id',
    ownerOnly,
    asyncHandler(async (req, res) => {
      // Disconnect, never hard-delete: historical payments must keep their shortcode.
      const till = await services.tills.update(req.params.id, { enabled: false, isDefault: false }, req.admin);
      res.json({ till });
    })
  );

  // ---- Customers ---------------------------------------------------------
  router.get(
    '/admin/customers',
    ownerOnly,
    asyncHandler(async (req, res) => {
      const limit = Math.min(Number(req.query.limit ?? 100) || 100, 500);
      const customers = await repo.customers.list({ limit });
      res.json({ customers });
    })
  );

  return router;
}
