import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';

import { config as defaultConfig } from './config.js';
import { createServices } from './services/index.js';
import { createAuthMiddleware } from './middleware/auth.js';
import { errorHandler, notFoundHandler, requestContext } from './middleware/common.js';
import { authRoutes } from './routes/auth.js';
import { catalogueRoutes } from './routes/catalogue.js';
import { transactionRoutes } from './routes/transactions.js';
import { paymentRoutes } from './routes/payments.js';
import { ussdRoutes } from './routes/ussd.js';
import { adminRoutes } from './routes/admin.js';

/**
 * Builds the Express application from an injected repository.
 * Tests pass the in-memory repository; production passes PostgreSQL.
 */
export function createApp({ repo, config = defaultConfig, fetchImpl } = {}) {
  if (!repo) throw new Error('createApp requires a repository');

  const services = createServices({ repo, config, fetchImpl });
  const middleware = createAuthMiddleware({ authService: services.auth, repo });

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 1);

  app.use(helmet({ crossOriginResourcePolicy: { policy: 'same-site' } }));
  app.use(
    cors({
      origin: config.allowedOrigins.length > 0 ? config.allowedOrigins : false,
      credentials: false
    })
  );
  app.use(express.json({ limit: '256kb' }));
  app.use(requestContext);
  app.use(
    rateLimit({
      windowMs: 60 * 1000,
      limit: 300,
      standardHeaders: true,
      legacyHeaders: false
    })
  );

  app.get('/api/v1/health', (_req, res) => {
    res.json({
      status: 'ok',
      service: 'mzazi-automated-machine-api',
      environment: config.env,
      testMode: config.testMode,
      time: new Date().toISOString()
    });
  });

  app.use('/api/v1/auth', authRoutes({ services, middleware }));
  app.use('/api/v1', adminRoutes({ services, middleware, repo, config }));
  app.use('/api/v1', catalogueRoutes({ services, middleware }));
  app.use('/api/v1/transactions', transactionRoutes({ services, middleware }));
  app.use('/api/v1/payments', paymentRoutes({ services, middleware, config }));
  app.use('/api/v1/ussd', ussdRoutes({ services, middleware }));

  app.use(notFoundHandler);
  app.use(errorHandler);

  return { app, services };
}
