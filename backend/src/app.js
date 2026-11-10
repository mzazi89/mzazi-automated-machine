import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { config as defaultConfig } from './config.js';
import { createServices } from './services/index.js';
import { createAuthMiddleware } from './middleware/auth.js';
import { errorHandler, notFoundHandler, requestContext } from './middleware/common.js';
import { authRoutes } from './routes/auth.js';
import { catalogueRoutes } from './routes/catalogue.js';
import { ownerRoutes } from './routes/owner.js';
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

  app.use(
    helmet({
      crossOriginResourcePolicy: { policy: 'same-site' },
      // The operator console is one self-contained file (no third-party assets), so
      // inline style/script is required. Everything it renders is HTML-escaped.
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          scriptSrc: ["'self'", "'unsafe-inline'"],
          styleSrc: ["'self'", "'unsafe-inline'"],
          imgSrc: ["'self'", 'data:'],
          connectSrc: ["'self'"],
          frameAncestors: ["'none'"],
          baseUri: ["'self'"]
        }
      }
    })
  );
  app.use(
    cors({
      origin: config.allowedOrigins.length > 0 ? config.allowedOrigins : false,
      credentials: false
    })
  );
  app.use(express.json({ limit: '256kb' }));

  // Operator console (static single page). On Vercel the same file is served from
  // /public at the edge; this path keeps `npm start` identical for local work.
  const publicDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
  app.use(express.static(publicDir, { index: 'index.html', maxAge: '1h' }));
  app.get('/console', (_req, res) => res.sendFile(path.join(publicDir, 'index.html')));
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
  app.use('/api/v1', ownerRoutes({ services, middleware, repo }));
  app.use('/api/v1/transactions', transactionRoutes({ services, middleware }));
  app.use('/api/v1/payments', paymentRoutes({ services, middleware, config }));
  app.use('/api/v1/ussd', ussdRoutes({ services, middleware }));

  app.use(notFoundHandler);
  app.use(errorHandler);

  return { app, services };
}
