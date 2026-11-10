import { fileURLToPath } from 'node:url';
import { getPool, closePool } from './pool.js';
import { config } from '../config.js';
import { hashPassword } from '../utils/crypto.js';
import { logger } from '../utils/logger.js';

/**
 * Creates the first administrator. Idempotent: if any admin exists, nothing changes.
 * Credentials come from the environment, never from source control.
 */
export async function seed() {
  const pool = getPool();

  const { rows } = await pool.query('SELECT count(*)::int AS count FROM admins');
  if (rows[0].count > 0) {
    logger.info('seed_skipped_admins_exist');
    return;
  }

  const { email, password, name } = config.bootstrapAdmin;
  if (!email || !password || password.length < 12) {
    throw new Error(
      'BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD (12+ chars) are required to seed'
    );
  }

  const passwordHash = await hashPassword(password);
  await pool.query(
    `INSERT INTO admins (email, name, role, password_hash) VALUES ($1, $2, 'super_admin', $3)`,
    [email.toLowerCase(), name, passwordHash]
  );

  logger.info('seed_admin_created', { email });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  seed()
    .then(async () => {
      await closePool();
      process.exit(0);
    })
    .catch(async (error) => {
      logger.error('seed_failed', { error: error.message });
      await closePool();
      process.exit(1);
    });
}
