import 'dotenv/config';

const isProduction = process.env.NODE_ENV === 'production';

/** Fails fast: a missing secret must stop the process, never silently degrade. */
function required(name, fallback = undefined) {
  const value = process.env[name] ?? fallback;
  if (value === undefined || value === '') {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function bool(name, fallback = false) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(raw.toLowerCase());
}

function int(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const parsed = Number.parseInt(raw, 10);
  if (Number.isNaN(parsed)) throw new Error(`Environment variable ${name} must be an integer`);
  return parsed;
}

export const config = {
  env: process.env.NODE_ENV ?? 'development',
  isProduction,
  port: int('PORT', 8080),
  publicBaseUrl: process.env.PUBLIC_BASE_URL ?? 'http://localhost:8080',

  databaseUrl: process.env.DATABASE_URL ?? '',
  pgSsl: process.env.PGSSL ?? (isProduction ? 'require' : 'disable'),
  dbPoolMax: int('DB_POOL_MAX', 10),

  jwtSecret: process.env.JWT_SECRET ?? '',
  jwtAccessTtl: process.env.JWT_ACCESS_TTL ?? '12h',
  jwtRefreshTtl: process.env.JWT_REFRESH_TTL ?? '30d',

  bootstrapAdmin: {
    email: process.env.BOOTSTRAP_ADMIN_EMAIL ?? '',
    password: process.env.BOOTSTRAP_ADMIN_PASSWORD ?? '',
    name: process.env.BOOTSTRAP_ADMIN_NAME ?? 'MZAZI Administrator'
  },

  mpesa: {
    environment: process.env.MPESA_ENVIRONMENT ?? 'sandbox',
    consumerKey: process.env.MPESA_CONSUMER_KEY ?? '',
    consumerSecret: process.env.MPESA_CONSUMER_SECRET ?? '',
    shortcode: process.env.MPESA_SHORTCODE ?? '',
    passkey: process.env.MPESA_PASSKEY ?? '',
    callbackUrl: process.env.MPESA_CALLBACK_URL ?? '',
    callbackSecret: process.env.MPESA_CALLBACK_SECRET ?? '',
    verifyWithQuery: bool('MPESA_VERIFY_WITH_QUERY', true),
    timeoutSeconds: int('MPESA_TIMEOUT_SECONDS', 60)
  },

  testMode: bool('TEST_MODE', !isProduction),
  duplicateWindowMinutes: int('DUPLICATE_WINDOW_MINUTES', 15),
  allowedOrigins: (process.env.ALLOWED_ORIGINS ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean),

  logLevel: process.env.LOG_LEVEL ?? 'info'
};

/**
 * Validates the configuration for a real deployment.
 * Called at boot in production; in development the API still starts so the
 * simulator and tests work without carrier credentials.
 */
export function assertProductionConfig(cfg = config) {
  const problems = [];
  if (!cfg.databaseUrl) problems.push('DATABASE_URL is required');
  if (!cfg.jwtSecret || cfg.jwtSecret.length < 32) {
    problems.push('JWT_SECRET must be at least 32 characters');
  }
  if (cfg.isProduction && cfg.testMode) {
    problems.push('TEST_MODE must be false in production');
  }
  if (cfg.isProduction) {
    if (!cfg.mpesa.consumerKey || !cfg.mpesa.consumerSecret) {
      problems.push('MPESA_CONSUMER_KEY and MPESA_CONSUMER_SECRET are required in production');
    }
    if (!cfg.mpesa.passkey || !cfg.mpesa.shortcode) {
      problems.push('MPESA_PASSKEY and MPESA_SHORTCODE are required in production');
    }
    if (!cfg.mpesa.callbackUrl.startsWith('https://')) {
      problems.push('MPESA_CALLBACK_URL must be HTTPS in production');
    }
    if (!cfg.publicBaseUrl.startsWith('https://')) {
      problems.push('PUBLIC_BASE_URL must be HTTPS in production');
    }
  }
  return problems;
}

export { required };
