import pg from 'pg';
import { config } from '../config.js';
import { logger } from '../utils/logger.js';

const { Pool } = pg;

let pool;

/**
 * Neon (and most hosted Postgres) requires TLS. `sslmode=require` in the connection
 * string is honoured; the explicit ssl option covers environments that strip it.
 */
export function getPool() {
  if (pool) return pool;

  if (!config.databaseUrl) {
    throw new Error('DATABASE_URL is not set. Copy .env.example to .env and configure Neon.');
  }

  pool = new Pool({
    connectionString: config.databaseUrl,
    max: config.dbPoolMax,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 10_000,
    ssl: config.pgSsl === 'disable' ? false : { rejectUnauthorized: false }
  });

  pool.on('error', (error) => {
    logger.error('postgres_pool_error', { error: error.message });
  });

  return pool;
}

export async function closePool() {
  if (pool) {
    await pool.end();
    pool = undefined;
  }
}
