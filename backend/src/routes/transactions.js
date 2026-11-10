import { Router } from 'express';
import { asyncHandler } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { createTransactionSchema } from './schemas.js';
import { transactionDto } from './serialize.js';

export function transactionRoutes({ services, middleware }) {
  const router = Router();

  router.post(
    '/',
    middleware.requireAdminOrDevice,
    validateBody(createTransactionSchema),
    asyncHandler(async (req, res) => {
      const result = await services.transactions.create({
        body: req.validated,
        idempotencyKey:
          req.headers['idempotency-key'] ?? req.headers['Idempotency-Key'] ?? undefined,
        device: req.device,
        actor: req.admin ? { type: 'admin', id: req.admin.id } : undefined,
        ip: req.ip
      });
      res.status(result.replay ? 200 : 201).json({
        ...transactionDto(result.transaction),
        idempotentReplay: result.replay,
        simulated: Boolean(result.simulated)
      });
    })
  );

  router.get(
    '/',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const result = await services.transactions.list({
        limit: req.query.limit,
        offset: req.query.offset,
        status: req.query.status,
        scope: req.admin
      });
      res.json(result.items.map(transactionDto));
    })
  );

  router.get(
    '/:id',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const transaction = await services.transactions.getById(req.params.id, req.admin);
      res.json(transactionDto(transaction));
    })
  );

  router.post(
    '/:id/cancel',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const transaction = await services.transactions.cancel(req.params.id, {
        type: req.admin ? 'admin' : 'device',
        id: req.admin?.id ?? req.device?.id
      }, req.admin);
      res.json(transactionDto(transaction));
    })
  );

  return router;
}
