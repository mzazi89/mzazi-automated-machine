import { logger } from '../utils/logger.js';

/**
 * Audit trail. Every privileged action is persisted; the logger keeps a copy so a
 * database outage cannot erase the fact that an action happened.
 */
export function createAuditService({ repo }) {
  async function record({ actorType = 'system', actorId = null, action, target = null, detail = null, ip = null }) {
    try {
      await repo.audit.record({ actorType, actorId, action, target, detail, ip });
    } catch (error) {
      logger.error('audit_write_failed', { action, error: error.message });
    }
  }

  return { record };
}
