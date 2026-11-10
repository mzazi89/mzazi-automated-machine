import { Router } from 'express';
import { asyncHandler } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { jobResultSchema, manualConfirmSchema } from './schemas.js';
import { jobDto, transactionDto, workflowDto } from './serialize.js';

/**
 * Device-facing delivery queue.
 *
 * Claiming is a lease, so two terminals polling in parallel cannot both execute a
 * delivery. Reporting a result is idempotent at the transaction level: the service
 * enforces the purchase state machine.
 */
export function ussdRoutes({ services, middleware }) {
  const router = Router();

  router.get(
    '/jobs/next',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const claimed = await services.ussd.claimNext(req.device?.id ?? req.admin?.id);
      if (!claimed) {
        res.status(204).end();
        return;
      }
      const { job, transaction, workflow } = claimed;
      res.json({
        ...jobDto(job),
        customerMsisdn: transaction.customerMsisdn,
        bundleName: transaction.bundleName,
        bundleSizeMb: transaction.bundleSizeMb,
        amountCents: Number(transaction.amountCents),
        workflow: workflow ? workflowDto(workflow) : null
      });
    })
  );

  router.post(
    '/jobs/:id/result',
    middleware.requireAdminOrDevice,
    validateBody(jobResultSchema),
    asyncHandler(async (req, res) => {
      const result = await services.ussd.reportResult(req.params.id, req.validated, {
        deviceId: req.device?.id,
        adminId: req.admin?.id
      });
      res.json({
        job: jobDto(result.job),
        transaction: transactionDto(result.transaction)
      });
    })
  );

  router.post(
    '/jobs/:id/manual-confirmation',
    middleware.requireAdminOrDevice,
    validateBody(manualConfirmSchema),
    asyncHandler(async (req, res) => {
      const result = await services.ussd.manualConfirm(req.params.id, req.validated, {
        deviceId: req.device?.id,
        adminId: req.admin?.id
      });
      res.json({
        job: jobDto(result.job),
        transaction: transactionDto(result.transaction)
      });
    })
  );

  router.get(
    '/jobs',
    middleware.requireAdmin,
    asyncHandler(async (req, res) => {
      const jobs = await services.ussd.listJobs({
        status: req.query.status,
        limit: Number(req.query.limit ?? 50)
      });
      res.json(jobs.map(jobDto));
    })
  );

  return router;
}
