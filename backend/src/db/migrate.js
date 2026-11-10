import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { getPool, closePool } from './pool.js';
import { logger } from '../utils/logger.js';

const here = path.dirname(fileURLToPath(import.meta.url));

/**
 * Applies schema.sql. The schema is written with IF NOT EXISTS / DROP TRIGGER IF EXISTS
 * so it is safe to re-run on every deploy.
 */
export async function migrate() {
  const pool = getPool();
  const sql = await fs.readFile(path.join(here, 'schema.sql'), 'utf8');
  logger.info('migration_started');
  await pool.query(sql);
  logger.info('migration_completed');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  migrate()
    .then(async () => {
      await closePool();
      process.exit(0);
    })
    .catch(async (error) => {
      logger.error('migration_failed', { error: error.message });
      await closePool();
      process.exit(1);
    });
}
