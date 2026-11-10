import { badRequest, notFound, unprocessable } from '../utils/errors.js';
import { validateWorkflow } from '../domain/workflowRules.js';

const NETWORKS = new Set(['SAFARICOM', 'AIRTEL', 'TELKOM']);

function assertNetwork(network) {
  const value = String(network ?? '').toUpperCase();
  if (!NETWORKS.has(value)) {
    throw unprocessable('network must be SAFARICOM, AIRTEL or TELKOM', 'invalid_network');
  }
  return value;
}

export function createCatalogueService({ repo, audit }) {
  async function listBundles({ network, includeDisabled = false } = {}) {
    const filter = network ? { network: assertNetwork(network), includeDisabled } : { includeDisabled };
    return repo.bundles.list(filter);
  }

  async function createBundle(input, actor = {}) {
    const network = assertNetwork(input?.network);
    const dataSizeMb = Number(input?.dataSizeMb);
    const sellingPriceCents = Number(input?.sellingPriceCents);

    if (!Number.isInteger(dataSizeMb) || dataSizeMb <= 0) {
      throw unprocessable('dataSizeMb must be a positive integer', 'invalid_size');
    }
    if (!Number.isInteger(sellingPriceCents) || sellingPriceCents <= 0) {
      throw unprocessable('sellingPriceCents must be a positive integer', 'invalid_price');
    }
    if (input?.workflowId) {
      const workflow = await repo.workflows.findById(input.workflowId);
      if (!workflow) throw notFound('Workflow not found');
      if (workflow.network !== network) {
        throw unprocessable('The workflow belongs to a different network', 'workflow_network_mismatch');
      }
      if (!workflow.enabled || !workflow.verifiedBy) {
        throw unprocessable(
          'The workflow must be verified and enabled before it can deliver this bundle',
          'workflow_not_verified'
        );
      }
    }

    const bundle = await repo.bundles.create({
      network,
      name: String(input.name ?? '').trim() || `${network} ${dataSizeMb}MB`,
      dataSizeMb,
      sellingPriceCents,
      costPriceCents: Number.isInteger(input?.costPriceCents) ? input.costPriceCents : 0,
      validityDays: Number.isInteger(input?.validityDays) ? input.validityDays : 30,
      workflowId: input.workflowId ?? null,
      enabled: input?.enabled !== false,
      sortOrder: Number.isInteger(input?.sortOrder) ? input.sortOrder : dataSizeMb
    });

    await audit?.record({
      actorType: actor.type ?? 'admin',
      actorId: actor.id ?? null,
      action: 'bundle.created',
      target: bundle.id,
      detail: { network, name: bundle.name, sellingPriceCents }
    });
    return bundle;
  }

  async function updateBundle(id, input, actor = {}) {
    const existing = await repo.bundles.findById(id);
    if (!existing) throw notFound('Bundle not found');

    const patch = {};
    if (input?.name !== undefined) patch.name = String(input.name).trim();
    if (input?.dataSizeMb !== undefined) patch.dataSizeMb = Number(input.dataSizeMb);
    if (input?.sellingPriceCents !== undefined) {
      patch.sellingPriceCents = Number(input.sellingPriceCents);
    }
    if (input?.costPriceCents !== undefined) patch.costPriceCents = Number(input.costPriceCents);
    if (input?.validityDays !== undefined) patch.validityDays = Number(input.validityDays);
    if (input?.workflowId !== undefined) patch.workflowId = input.workflowId;
    if (input?.enabled !== undefined) patch.enabled = Boolean(input.enabled);
    if (input?.sortOrder !== undefined) patch.sortOrder = Number(input.sortOrder);

    const updated = await repo.bundles.update(id, patch);
    await audit?.record({
      actorType: actor.type ?? 'admin',
      actorId: actor.id ?? null,
      action: 'bundle.updated',
      target: id,
      detail: patch
    });
    return updated;
  }

  async function deleteBundle(id, actor = {}) {
    const existing = await repo.bundles.findById(id);
    if (!existing) throw notFound('Bundle not found');
    await repo.bundles.softDelete(id);
    await audit?.record({
      actorType: actor.type ?? 'admin',
      actorId: actor.id ?? null,
      action: 'bundle.deleted',
      target: id
    });
    return { deleted: true };
  }

  async function listWorkflows({ network } = {}) {
    const filter = network ? { network: assertNetwork(network) } : {};
    return repo.workflows.list(filter);
  }

  async function createWorkflow(input, actor = {}) {
    const network = assertNetwork(input?.network);
    const steps = Array.isArray(input?.steps) ? input.steps : [];
    const verifiedBy = input?.verifiedBy ? String(input.verifiedBy).trim() : null;
    const enabled = input?.enabled === true;

    const problems = validateWorkflow({
      steps,
      successPattern: input?.successPattern,
      verifiedBy,
      enabled
    });
    if (problems.length > 0) {
      throw badRequest('Workflow rejected', 'invalid_workflow', problems);
    }

    const workflow = await repo.workflows.create({
      network,
      name: String(input.name ?? '').trim() || `${network} verified workflow`,
      steps,
      successPattern: input.successPattern,
      failurePattern: input.failurePattern ?? null,
      timeoutMs: Number.isInteger(input?.timeoutMs) ? input.timeoutMs : 45000,
      enabled,
      verifiedBy
    });

    await audit?.record({
      actorType: actor.type ?? 'admin',
      actorId: actor.id ?? null,
      action: 'workflow.created',
      target: workflow.id,
      detail: { network, steps: steps.length, verifiedBy }
    });
    return workflow;
  }

  async function updateWorkflow(id, input, actor = {}) {
    const existing = await repo.workflows.findById(id);
    if (!existing) throw notFound('Workflow not found');

    const next = {
      steps: input?.steps ?? existing.steps,
      successPattern: input?.successPattern ?? existing.successPattern,
      verifiedBy: input?.verifiedBy !== undefined ? input.verifiedBy : existing.verifiedBy,
      enabled: input?.enabled !== undefined ? Boolean(input.enabled) : existing.enabled
    };

    const problems = validateWorkflow(next);
    if (problems.length > 0) {
      throw badRequest('Workflow rejected', 'invalid_workflow', problems);
    }

    const updated = await repo.workflows.update(id, {
      name: input?.name,
      steps: next.steps,
      successPattern: next.successPattern,
      failurePattern: input?.failurePattern,
      timeoutMs: input?.timeoutMs,
      enabled: next.enabled,
      verifiedBy: next.verifiedBy
    });

    await audit?.record({
      actorType: actor.type ?? 'admin',
      actorId: actor.id ?? null,
      action: 'workflow.updated',
      target: id,
      detail: { enabled: next.enabled }
    });
    return updated;
  }

  return {
    listBundles,
    createBundle,
    updateBundle,
    deleteBundle,
    listWorkflows,
    createWorkflow,
    updateWorkflow
  };
}
