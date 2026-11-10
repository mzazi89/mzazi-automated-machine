import { Router } from 'express';
import { asyncHandler } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { bundleSchema, bundleUpdateSchema, workflowSchema, workflowUpdateSchema } from './schemas.js';
import { bundleDto, workflowDto } from './serialize.js';

export function catalogueRoutes({ services, middleware }) {
  const router = Router();

  // Catalogue is readable by signed-in admins and provisioned terminals alike.
  router.get(
    '/bundles',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const includeDisabled =
        String(req.query.includeDisabled ?? '').toLowerCase() === 'true' && Boolean(req.admin);
      const items = await services.catalogue.listBundles({
        network: req.query.network,
        includeDisabled
      });
      res.json(items.map(bundleDto));
    })
  );

  router.post(
    '/bundles',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    validateBody(bundleSchema),
    asyncHandler(async (req, res) => {
      const bundle = await services.catalogue.createBundle(req.validated, {
        type: 'admin',
        id: req.admin.id
      });
      res.status(201).json(bundleDto(bundle));
    })
  );

  router.patch(
    '/bundles/:id',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    validateBody(bundleUpdateSchema),
    asyncHandler(async (req, res) => {
      const bundle = await services.catalogue.updateBundle(req.params.id, req.validated, {
        type: 'admin',
        id: req.admin.id
      });
      res.json(bundleDto(bundle));
    })
  );

  router.delete(
    '/bundles/:id',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    asyncHandler(async (req, res) => {
      await services.catalogue.deleteBundle(req.params.id, { type: 'admin', id: req.admin.id });
      res.json({ deleted: true });
    })
  );

  router.get(
    '/workflows',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const items = await services.catalogue.listWorkflows({ network: req.query.network });
      res.json(items.map(workflowDto));
    })
  );

  router.post(
    '/workflows',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    validateBody(workflowSchema),
    asyncHandler(async (req, res) => {
      const workflow = await services.catalogue.createWorkflow(req.validated, {
        type: 'admin',
        id: req.admin.id
      });
      res.status(201).json(workflowDto(workflow));
    })
  );

  router.patch(
    '/workflows/:id',
    middleware.requireAdmin,
    middleware.requireRole('admin', 'super_admin'),
    validateBody(workflowUpdateSchema),
    asyncHandler(async (req, res) => {
      const workflow = await services.catalogue.updateWorkflow(req.params.id, req.validated, {
        type: 'admin',
        id: req.admin.id
      });
      res.json(workflowDto(workflow));
    })
  );

  return router;
}
