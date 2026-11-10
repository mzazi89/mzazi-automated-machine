import { createApp } from '../src/app.js';
import { createMemoryRepository } from '../src/repositories/memoryRepository.js';
import { hashPassword, sha256 } from '../src/utils/crypto.js';

export const ADMIN_EMAIL = 'admin@mzazitech.test';
export const ADMIN_PASSWORD = 'StrongPassword123!';
export const DEVICE_KEY = 'device-key-for-tests-only';

export function testConfig(overrides = {}) {
  return {
    env: 'test',
    isProduction: false,
    port: 0,
    publicBaseUrl: 'http://localhost:8080',
    databaseUrl: '',
    pgSsl: 'disable',
    dbPoolMax: 2,
    jwtSecret: 'test-secret-test-secret-test-secret-1234',
    jwtAccessTtl: '1h',
    jwtRefreshTtl: '1d',
    bootstrapAdmin: { email: '', password: '', name: '' },
    mpesa: {
      environment: 'sandbox',
      consumerKey: '',
      consumerSecret: '',
      shortcode: '',
      passkey: '',
      callbackUrl: 'http://localhost:8080/api/v1/payments/mpesa/callback',
      callbackSecret: 'test-callback-secret',
      verifyWithQuery: false,
      timeoutSeconds: 60
    },
    testMode: true,
    tillEncryptionKey: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    duplicateWindowMinutes: 15,
    allowedOrigins: [],
    logLevel: 'error',
    ...overrides
  };
}

/** Builds an app backed by the in-memory repository, with an admin and a device. */
export async function buildTestApp(configOverrides = {}) {
  const repo = createMemoryRepository();
  const config = testConfig(configOverrides);

  const passwordHash = await hashPassword(ADMIN_PASSWORD);
  const admin = await repo.admins.create({
    email: ADMIN_EMAIL,
    name: 'Test Admin',
    role: 'owner',
    passwordHash
  });

  const device = await repo.devices.create({
    label: 'Test terminal',
    apiKeyHash: sha256(DEVICE_KEY)
  });

  const { app, services } = createApp({ repo, config });

  return { app, services, repo, config, admin, device };
}

/** Headers that authenticate an Android terminal. */
export function headersFor(device) {
  return {
    'X-Device-Id': device.id,
    'X-Device-Key': DEVICE_KEY
  };
}

/** Creates a verified network workflow plus a sellable bundle. */
export async function seedCatalogue(repo, network = 'SAFARICOM') {
  const workflow = await repo.workflows.create({
    network,
    name: 'Verified test workflow',
    steps: [
      {
        index: 0,
        label: 'Purchase',
        requestTemplate: '*100*{bundleSizeMb}*{msisdnLocal}#',
        expectedPattern: null,
        sendAsSessionInput: false,
        timeoutMs: 45000
      }
    ],
    successPattern: '(?i)successful',
    failurePattern: '(?i)failed',
    timeoutMs: 45000,
    enabled: true,
    verifiedBy: 'QA Test'
  });

  const bundle = await repo.bundles.create({
    network,
    name: `${network} 1GB`,
    dataSizeMb: 1024,
    sellingPriceCents: 10000,
    costPriceCents: 8500,
    validityDays: 30,
    workflowId: workflow.id,
    enabled: true,
    sortOrder: 1024
  });

  return { workflow, bundle };
}
