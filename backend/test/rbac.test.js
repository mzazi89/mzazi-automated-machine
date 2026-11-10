import test from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';

import { buildTestApp, seedCatalogue, headersFor, ADMIN_EMAIL, ADMIN_PASSWORD } from './helpers.js';

/**
 * Owner / seller separation.
 *
 * The machine must be configurable by the owner only, and a seller must only ever
 * see and sell — these tests assert the API refuses everything else.
 */
test('owner and seller separation', async (t) => {
  const { app, repo, device } = await buildTestApp();
  const deviceAuth = headersFor(device);

  const ownerLogin = await request(app)
    .post('/api/v1/auth/login')
    .send({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD });
  assert.equal(ownerLogin.status, 200);
  const ownerToken = ownerLogin.body.accessToken;
  const auth = (token = ownerToken) => ({ Authorization: `Bearer ${token}` });

  let sellerId = '';
  let bundleId = '';
  let tillId = '';

  await t.test('owner is identified as owner', async () => {
    const me = await request(app).get('/api/v1/auth/me').set(auth());
    assert.equal(me.status, 200);
    assert.equal(me.body.role, 'owner');
  });

  await t.test('owner creates a seller account', async () => {
    const res = await request(app)
      .post('/api/v1/admin/sellers')
      .set(auth())
      .send({ email: 'seller@mzazi.shop', name: 'Njeri Seller', password: 'SellerPass123!' });
    assert.equal(res.status, 201);
    assert.equal(res.body.seller.role, 'seller');
    assert.equal(res.body.seller.email, 'seller@mzazi.shop');
    assert.equal(res.body.seller.passwordHash, undefined, 'hash must never be returned');
    sellerId = res.body.seller.id;
  });

  await t.test('a seller cannot be created with a duplicate email', async () => {
    const res = await request(app)
      .post('/api/v1/admin/sellers')
      .set(auth())
      .send({ email: 'seller@mzazi.shop', name: 'Duplicate', password: 'SellerPass123!' });
    assert.equal(res.status, 409);
    assert.equal(res.body.code, 'email_taken');
  });

  await t.test('seller signs in and is scoped as seller', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'seller@mzazi.shop', password: 'SellerPass123!' });
    assert.equal(res.status, 200);
    assert.equal(res.body.admin.role, 'seller');
  });

  const sellerToken = async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'seller@mzazi.shop', password: 'SellerPass123!' });
    return res.body.accessToken;
  };

  await t.test('owner configures catalogue and a till', async () => {
    const catalogue = await seedCatalogue(repo);
    bundleId = catalogue.bundle.id;

    const till = await request(app)
      .post('/api/v1/admin/tills')
      .set(auth())
      .send({
        network: 'SAFARICOM',
        label: 'Safaricom Main Till',
        shortcode: '43210',
        accountType: 'TILL',
        passkey: 'super-secret-passkey-value',
        isDefault: true
      });
    assert.equal(till.status, 201);
    assert.equal(till.body.till.shortcode, '43210');
    assert.equal(till.body.till.hasPasskey, true);
    assert.equal(till.body.till.passkeyHint, '••••alue');
    tillId = till.body.till.id;

    const raw = JSON.stringify(till.body);
    assert.equal(raw.includes('super-secret-passkey-value'), false, 'passkey must never be returned');
  });

  await t.test('the passkey is encrypted at rest', async () => {
    const stored = await repo.tills.findById(tillId);
    assert.ok(stored.passkeyCipher);
    assert.equal(stored.passkeyCipher.includes('super-secret'), false);
  });

  await t.test('duplicate till for the same network is refused', async () => {
    const res = await request(app)
      .post('/api/v1/admin/tills')
      .set(auth())
      .send({ network: 'SAFARICOM', label: 'Second', shortcode: '43210' });
    assert.equal(res.status, 409);
    assert.equal(res.body.code, 'duplicate_till');
  });

  await t.test('seller is refused every configuration endpoint', async () => {
    const token = await sellerToken();

    const sellerList = await request(app).get('/api/v1/admin/sellers').set(auth(token));
    assert.equal(sellerList.status, 403);
    assert.equal(sellerList.body.code, 'insufficient_role');

    const bundle = await request(app)
      .post('/api/v1/bundles')
      .set(auth(token))
      .send({ network: 'SAFARICOM', name: 'Hack', dataSizeMb: 1024, sellingPriceCents: 100 });
    assert.equal(bundle.status, 403);

    const till = await request(app)
      .post('/api/v1/admin/tills')
      .set(auth(token))
      .send({ network: 'AIRTEL', label: 'Rogue', shortcode: '9999' });
    assert.equal(till.status, 403);

    const workflow = await request(app)
      .post('/api/v1/workflows')
      .set(auth(token))
      .send({ network: 'SAFARICOM', name: 'Rogue', steps: [], enabled: false });
    assert.equal(workflow.status, 403);

    const customers = await request(app).get('/api/v1/admin/customers').set(auth(token));
    assert.equal(customers.status, 403);

    const devices = await request(app).get('/api/v1/admin/devices').set(auth(token));
    assert.equal(devices.status, 403);
  });

  await t.test('seller can still sell', async () => {
    const token = await sellerToken();
    const res = await request(app)
      .post('/api/v1/transactions')
      .set(auth(token))
      .set('Idempotency-Key', 'seller-sale-0001')
      .send({ customerMsisdn: '0722334455', network: 'SAFARICOM', bundleId });
    assert.equal(res.status, 201);
    assert.equal(res.body.sellerId, sellerId);
  });

  await t.test('a seller only sees their own sales', async () => {
    // A sale rung up on the paired terminal, not by the seller.
    await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .set('Idempotency-Key', 'device-sale-0001')
      .send({ customerMsisdn: '0733445566', network: 'SAFARICOM', bundleId });

    const token = await sellerToken();
    const sellerView = await request(app).get('/api/v1/transactions').set(auth(token));
    assert.equal(sellerView.status, 200);
    assert.equal(sellerView.body.length, 1);
    assert.equal(sellerView.body[0].sellerId, sellerId);

    const ownerView = await request(app).get('/api/v1/transactions').set(auth());
    assert.equal(ownerView.status, 200);
    assert.equal(ownerView.body.length, 2);
  });

  await t.test('a seller cannot read another seller\'s transaction', async () => {
    const ownerView = await request(app).get('/api/v1/transactions').set(auth());
    const deviceSale = ownerView.body.find((t) => t.sellerId === null);
    const token = await sellerToken();

    const res = await request(app).get(`/api/v1/transactions/${deviceSale.id}`).set(auth(token));
    assert.equal(res.status, 404);
  });

  await t.test('dashboard totals are scoped to the caller', async () => {
    const token = await sellerToken();
    const sellerSummary = await request(app).get('/api/v1/dashboard/summary').set(auth(token));
    assert.equal(sellerSummary.status, 200);
    assert.equal(sellerSummary.body.totalTransactions, 1);

    const ownerSummary = await request(app).get('/api/v1/dashboard/summary').set(auth());
    assert.equal(ownerSummary.body.totalTransactions, 2);
  });

  await t.test('owner sees aggregated customers', async () => {
    const res = await request(app).get('/api/v1/admin/customers').set(auth());
    assert.equal(res.status, 200);
    const numbers = res.body.customers.map((c) => c.msisdn).sort();
    assert.deepEqual(numbers, ['254722334455', '254733445566']);
    assert.equal(res.body.customers[0].orders, 1);
  });

  await t.test('owner account cannot be disabled, and nobody can disable themselves', async () => {
    const me = await request(app).get('/api/v1/auth/me').set(auth());
    const res = await request(app)
      .patch(`/api/v1/admin/sellers/${me.body.id}`)
      .set(auth())
      .send({ disabled: true });
    assert.equal(res.status, 422);
    assert.ok(['last_owner', 'self_disable'].includes(res.body.code));
  });

  await t.test('owner can disable a seller and that seller can no longer sign in', async () => {
    const res = await request(app)
      .patch(`/api/v1/admin/sellers/${sellerId}`)
      .set(auth())
      .send({ disabled: true });
    assert.equal(res.status, 200);
    assert.equal(res.body.seller.disabled, true);

    const login = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'seller@mzazi.shop', password: 'SellerPass123!' });
    assert.equal(login.status, 403);
  });
});
