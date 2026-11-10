export class AppError extends Error {
  constructor(statusCode, message, code = 'error', details = undefined) {
    super(message);
    this.name = 'AppError';
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }
}

export const badRequest = (message, code = 'bad_request', details) =>
  new AppError(400, message, code, details);

export const unauthorized = (message = 'Authentication required', code = 'unauthorized') =>
  new AppError(401, message, code);

export const forbidden = (message = 'Not allowed', code = 'forbidden') =>
  new AppError(403, message, code);

export const notFound = (message = 'Not found', code = 'not_found') =>
  new AppError(404, message, code);

export const conflict = (message, code = 'conflict') => new AppError(409, message, code);

export const unprocessable = (message, code = 'unprocessable', details) =>
  new AppError(422, message, code, details);

export const tooMany = (message = 'Too many requests', code = 'rate_limited') =>
  new AppError(429, message, code);

/** Wraps an async route so rejected promises reach the error middleware. */
export const asyncHandler = (fn) => (req, res, next) => {
  Promise.resolve(fn(req, res, next)).catch(next);
};
