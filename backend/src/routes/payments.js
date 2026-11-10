import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { asyncHandler, unauthorized } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { simulatePaymentSchema } from './schemas.js';
import { transactionDto } from './serialize.js';
import { logger } from '../utils/logger.js';

/**
 * Payment routes.
 *
 * The callback is intentionally public (Safaricom calls it) but protected by a secret
 * path segment plus a strict lookup: an unknown CheckoutRequestID can never settle
 * anything. A device-side SMS is never accepted here as proof of payment.
 */
export function paymentRoutes({ services, middleware, config }) {
  const router = Router();

  const callbackLimiter = rateLimit({
    windowMs: 60 * 1000,
    limit: 120,
    standardHeaders: true,
    legacyHeaders: false
  });

  function assertCallbackSecret(req) {
    const expected = config.mpesa.callbackSecret;
    if (!expected) {
      if (config.isProduction) {
        throw unauthorized('Callback secret is not configured', 'callback_secret_missing');
      }
      return;
    }
    const provided =
      req.params.secret ??
      req.query.secret ??
      req.headers['x-mzazi-callback-secret'];
    if (!provided || provided !== expected) {
      throw unauthorized('Invalid callback secret', 'invalid_callback_secret');
    }
  }

  const handleCallback = asyncHandler(async (req, res) => {
    assertCallbackSecret(req);
    const result = await services.payments.handleCallback(req.body);

    // Daraja expects a 200 with a body; anything else triggers retries.
    res.json({
      ResultCode: 0,
      ResultDesc: 'Accepted',
      duplicate: Boolean(result.duplicate),
      ignored: Boolean(result.ignored)
    });
  });

  router.post('/mpesa/callback', callbackLimiter, handleCallback);
  router.post('/mpesa/callback/:secret', callbackLimiter, handleCallback);

  // Daraja timeout notification (optional endpoint configured in the portal).
  router.post(
    '/mpesa/timeout',
    callbackLimiter,
    asyncHandler(async (req, res) => {
      logger.warn('M-Pesa timeout notification', { body: req.body });
      res.json({ ResultCode: 0, ResultDesc: 'Accepted' });
    })
  );

  router.post(
    '/mpesa/stk-push',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      const transaction = await services.transactions.getById(req.body?.transactionId);
      const result = await services.payments.initiateStkPush({
        transaction,
        phone: req.body?.phone ?? transaction.customerMsisdn
      });
      res.json({
        transaction: transactionDto(result.transaction),
        checkoutRequestId: result.payment.checkoutRequestId,
        simulated: result.simulated
      });
    })
  );

  router.get(
    '/:transactionId/status',
    middleware.requireAdminOrDevice,
    asyncHandler(async (req, res) => {
      res.json(await services.payments.statusFor(req.params.transactionId));
    })
  );

  // TEST MODE ONLY. Refuses to run unless TEST_MODE=true on the server.
  router.post(
    '/test/simulate',
    middleware.requireAdminOrDevice,
    validateBody(simulatePaymentSchema),
    asyncHandler(async (req, res) => {
      const result = await services.payments.simulate(req.validated);
      res.json({
        settled: !result.duplicate,
        transaction: result.transaction ? transactionDto(result.transaction) : null
      });
    })
  );

  return router;
}
