import crypto from 'node:crypto';
import { AppError } from '../utils/errors.js';
import { logger } from '../utils/logger.js';

/** Correlates a client request with its log lines and error response. */
export function requestContext(req, res, next) {
  req.id = req.headers['x-request-id'] ?? crypto.randomUUID();
  res.setHeader('X-Request-Id', req.id);
  const startedAt = process.hrtime.bigint();

  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - startedAt) / 1e6;
    logger.info('request', {
      requestId: req.id,
      method: req.method,
      path: req.path,
      status: res.statusCode,
      durationMs: Math.round(durationMs)
    });
  });
  next();
}

/** Wraps a zod schema; rejects 400 with field-level detail. */
export function validateBody(schema) {
  return (req, _res, next) => {
    const result = schema.safeParse(req.body ?? {});
    if (!result.success) {
      const details = result.error.issues.map((issue) => ({
        path: issue.path.join('.'),
        message: issue.message
      }));
      return next(new AppError(400, 'Request validation failed', 'validation_error', details));
    }
    req.validated = result.data;
    return next();
  };
}

/* eslint-disable no-unused-vars */
export function errorHandler(err, req, res, _next) {
  const isKnown = err instanceof AppError;
  const status = isKnown ? err.statusCode : 500;

  if (!isKnown) {
    logger.error('unhandled_error', {
      requestId: req.id,
      path: req.path,
      error: err?.message,
      stack: process.env.NODE_ENV === 'production' ? undefined : err?.stack
    });
  }

  // Postgres check/unique violations are mapped to a client-safe 4xx.
  let code = err.code;
  let message = err.message;
  if (!isKnown && err.code === '23514') {
    code = 'constraint_violation';
    message = 'The request violates a data rule';
  }
  if (!isKnown && err.code === '23505') {
    code = 'duplicate';
    message = 'That record already exists';
  }

  res.status(status).json({
    error: isKnown ? err.message : status === 500 ? 'Internal server error' : message,
    message: isKnown ? err.message : status === 500 ? 'Internal server error' : message,
    code: code ?? 'error',
    details: isKnown ? err.details : undefined,
    requestId: req.id
  });
}

export function notFoundHandler(req, res) {
  res.status(404).json({
    error: `No route for ${req.method} ${req.path}`,
    code: 'route_not_found',
    requestId: req.id
  });
}
