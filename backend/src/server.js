import { config, assertProductionConfig } from './config.js';
import { getPool, closePool } from './db/pool.js';
import { createPgRepository } from './repositories/pgRepository.js';
import { createApp } from './app.js';
import { logger } from './utils/logger.js';

const problems = assertProductionConfig(config);
if (problems.length > 0) {
  if (config.isProduction) {
    logger.error('configuration_invalid', { problems });
    process.exit(1);
  }
  logger.warn('configuration_incomplete', { problems });
}

const pool = getPool();
const repo = createPgRepository(pool);
const { app } = createApp({ repo, config });

const server = app.listen(config.port, () => {
  logger.info('server_started', {
    port: config.port,
    environment: config.env,
    testMode: config.testMode
  });
});

async function shutdown(signal) {
  logger.info('shutdown_initiated', { signal });
  server.close(async () => {
    await closePool();
    logger.info('shutdown_complete');
    process.exit(0);
  });
  // Do not hang forever on stuck sockets.
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('unhandledRejection', (reason) => {
  logger.error('unhandled_rejection', { reason: String(reason) });
});
