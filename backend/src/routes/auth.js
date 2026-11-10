import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { asyncHandler } from '../utils/errors.js';
import { validateBody } from '../middleware/common.js';
import { loginSchema, refreshSchema } from './schemas.js';

export function authRoutes({ services, middleware }) {
  const router = Router();

  const loginLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    limit: 10,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Too many sign-in attempts. Try again later.', code: 'rate_limited' }
  });

  router.post(
    '/login',
    loginLimiter,
    validateBody(loginSchema),
    asyncHandler(async (req, res) => {
      const tokens = await services.auth.login({
        email: req.validated.email,
        password: req.validated.password,
        ip: req.ip
      });
      res.json(tokens);
    })
  );

  router.post(
    '/refresh',
    validateBody(refreshSchema),
    asyncHandler(async (req, res) => {
      const tokens = await services.auth.refresh(req.validated.refreshToken);
      res.json(tokens);
    })
  );

  router.get(
    '/me',
    middleware.requireAdmin,
    asyncHandler(async (req, res) => {
      res.json(await services.auth.me(req.admin.id));
    })
  );

  return router;
}
