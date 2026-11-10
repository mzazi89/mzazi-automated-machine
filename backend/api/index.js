import { createApp } from '../src/app.js';
import { createPgRepository } from '../src/repositories/pgRepository.js';
import { getPool } from '../src/db/pool.js';

/**
 * Vercel serverless entrypoint.
 *
 * The same Express application used by `npm start` is exported here, so local and
 * production behaviour cannot drift apart. The Neon pool is created once per warm
 * lambda instance and capped by DB_POOL_MAX (pooled Neon URL recommended).
 */
const pool = getPool();
const repo = createPgRepository(pool);
const { app } = createApp({ repo });

export default app;
