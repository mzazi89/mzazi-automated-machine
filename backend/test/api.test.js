import test from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';

import { buildTestApp, seedCatalogue, headersFor, ADMIN_EMAIL, ADMIN_PASSWORD } from './helpers.js';

test('MZAZI API', async (t) => {
  const { app, services, repo, device } = await buildTestApp();
  const deviceAuth = headersFor(device);

  let adminToken = '';
  let bundleId = '';
  let workflowId = '';

  await t.test('health endpoint reports service state', async () => {
    const res = await request(app).get('/api/v1/health').expect(200);
    assert.equal(res.body.status, 'ok');
    assert.equal(res.body.testMode, true);
  });

  await t.test('login rejects a wrong password without revealing the account', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: ADMIN_EMAIL, password: 'WrongPassword123!' })
      .expect(401);
    assert.equal(res.body.code, 'invalid_credentials');
  });

  await t.test('login returns a bearer token for valid credentials', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD })
      .expect(200);

    assert.ok(res.body.accessToken);
    assert.ok(res.body.refreshToken);
    assert.equal(res.body.admin.email, ADMIN_EMAIL);
    adminToken = res.body.accessToken;
  });

  await t.test('protected route rejects anonymous callers', async () => {
    await request(app).get('/api/v1/auth/me').expect(401);
  });

  await t.test('protected route accepts the admin token', async () => {
    const res = await request(app)
      .get('/api/v1/auth/me')
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);
    assert.equal(res.body.email, ADMIN_EMAIL);
  });

  await t.test('a workflow without a verifier is refused', async () => {
    const res = await request(app)
      .post('/api/v1/workflows')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        network: 'SAFARICOM',
        name: 'Unverified flow',
        steps: [{ index: 0, label: 'Buy', requestTemplate: '*100*{amount}#' }],
        successPattern: 'successful',
        enabled: true
      })
      .expect(400);
    assert.equal(res.body.code, 'invalid_workflow');
  });

  await t.test('a malformed USSD code is refused', async () => {
    const res = await request(app)
      .post('/api/v1/workflows')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        network: 'SAFARICOM',
        name: 'Bad flow',
        steps: [{ index: 0, label: 'Buy', requestTemplate: 'send me data please' }],
        successPattern: 'successful',
        verifiedBy: 'QA'
      })
      .expect(400);
    assert.ok(res.body.details.length > 0);
  });

  await t.test('a verified workflow is stored', async () => {
    const res = await request(app)
      .post('/api/v1/workflows')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        network: 'SAFARICOM',
        name: 'Verified Safaricom data flow',
        steps: [
          { index: 0, label: 'Buy data', requestTemplate: '*100*{bundleSizeMb}*{msisdnLocal}#' }
        ],
        successPattern: '(?i)successful',
        failurePattern: '(?i)failed',
        verifiedBy: 'Operator ref 2024-01',
        enabled: true
      })
      .expect(201);

    workflowId = res.body.id;
    assert.equal(res.body.enabled, true);
    assert.equal(res.body.steps.length, 1);
  });

  await t.test('a bundle linked to the workflow is created', async () => {
    const res = await request(app)
      .post('/api/v1/bundles')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        network: 'SAFARICOM',
        name: 'Safaricom 1GB',
        dataSizeMb: 1024,
        sellingPriceCents: 10000,
        costPriceCents: 8500,
        validityDays: 30,
        workflowId
      })
      .expect(201);

    bundleId = res.body.id;
    assert.equal(res.body.sellingPriceCents, 10000);
  });

  await t.test('a terminal can read the catalogue but cannot edit it', async () => {
    const list = await request(app)
      .get('/api/v1/bundles')
      .set(deviceAuth)
      .expect(200);
    assert.equal(list.body.length, 1);

    await request(app)
      .post('/api/v1/bundles')
      .set(deviceAuth)
      .send({ network: 'AIRTEL', name: 'x', dataSizeMb: 100, sellingPriceCents: 100 })
      .expect(401);
  });

  await t.test('a transaction requires an Idempotency-Key', async () => {
    const res = await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .send({ customerMsisdn: '0712345678', network: 'SAFARICOM', bundleId })
      .expect(400);
    assert.equal(res.body.code, 'idempotency_key_required');
  });

  await t.test('a transaction is created pending payment', async () => {
    const res = await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .set('Idempotency-Key', 'idem-key-0001')
      .send({ customerMsisdn: '0712345678', network: 'SAFARICOM', bundleId })
      .expect(201);

    assert.equal(res.body.paymentStatus, 'PENDING');
    assert.equal(res.body.amountCents, 10000);
    assert.ok(res.body.checkoutRequestId, 'STK push should produce a checkout request id');
    assert.equal(res.body.idempotentReplay, false);
  });

  await t.test('the same Idempotency-Key returns the same transaction', async () => {
    const first = await request(app)
      .get('/api/v1/transactions')
      .set(deviceAuth)
      .expect(200);
    const original = first.body[0];

    const res = await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .set('Idempotency-Key', 'idem-key-0001')
      .send({ customerMsisdn: '0712345678', network: 'SAFARICOM', bundleId })
      .expect(200);

    assert.equal(res.body.id, original.id);
    assert.equal(res.body.idempotentReplay, true);
  });

  await t.test('a second active purchase for the same number is refused', async () => {
    const res = await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .set('Idempotency-Key', 'idem-key-0002')
      .send({ customerMsisdn: '0712345678', network: 'SAFARICOM', bundleId })
      .expect(409);
    assert.equal(res.body.code, 'duplicate_active_purchase');
  });

  await t.test('no delivery job exists before payment is verified', async () => {
    const res = await request(app)
      .get('/api/v1/ussd/jobs/next')
      .set(deviceAuth)
      .expect(204);
    assert.deepEqual(res.body, {});
  });

  let transactionId = '';
  let checkoutRequestId = '';

  await t.test('the Daraja callback settles the payment', async () => {
    const list = await request(app).get('/api/v1/transactions').set(deviceAuth).expect(200);
    transactionId = list.body[0].id;
    checkoutRequestId = list.body[0].checkoutRequestId;

    const callbackBody = {
      Body: {
        stkCallback: {
          MerchantRequestID: 'merchant-1',
          CheckoutRequestID: checkoutRequestId,
          ResultCode: 0,
          ResultDesc: 'The service request is processed successfully.',
          CallbackMetadata: {
            Item: [
              { Name: 'Amount', Value: 100 },
              { Name: 'MpesaReceiptNumber', Value: 'NLJ7RT61SV' },
              { Name: 'PhoneNumber', Value: 254712345678 }
            ]
          }
        }
      }
    };

    const res = await request(app)
      .post('/api/v1/payments/mpesa/callback/test-callback-secret')
      .send(callbackBody)
      .expect(200);
    assert.equal(res.body.duplicate, false);

    const txn = await request(app)
      .get(`/api/v1/transactions/${transactionId}`)
      .set(deviceAuth)
      .expect(200);
    assert.equal(txn.body.paymentStatus, 'PAID');
    assert.equal(txn.body.mpesaReceipt, 'NLJ7RT61SV');
  });

  await t.test('a replayed callback does not create a second delivery', async () => {
    const callbackBody = {
      Body: {
        stkCallback: {
          CheckoutRequestID: checkoutRequestId,
          ResultCode: 0,
          ResultDesc: 'The service request is processed successfully.',
          CallbackMetadata: {
            Item: [
              { Name: 'Amount', Value: 100 },
              { Name: 'MpesaReceiptNumber', Value: 'NLJ7RT61SV' }
            ]
          }
        }
      }
    };

    const res = await request(app)
      .post('/api/v1/payments/mpesa/callback/test-callback-secret')
      .send(callbackBody)
      .expect(200);
    assert.equal(res.body.duplicate, true);

    const jobs = await repo.ussdJobs.list({});
    assert.equal(jobs.length, 1, 'exactly one delivery job must exist');
  });

  await t.test('a callback with the wrong secret is rejected', async () => {
    await request(app)
      .post('/api/v1/payments/mpesa/callback/wrong-secret')
      .send({ Body: { stkCallback: { CheckoutRequestID: checkoutRequestId, ResultCode: 0 } } })
      .expect(401);
  });

  await t.test('a callback for an unknown reference is acknowledged but ignored', async () => {
    const res = await request(app)
      .post('/api/v1/payments/mpesa/callback/test-callback-secret')
      .send({
        Body: {
          stkCallback: {
            CheckoutRequestID: 'ws_CO_DOES_NOT_EXIST',
            ResultCode: 0,
            ResultDesc: 'ok'
          }
        }
      })
      .expect(200);
    assert.equal(res.body.ignored, true);
  });

  let jobId = '';

  await t.test('the paid transaction produces exactly one claimed job', async () => {
    const res = await request(app)
      .get('/api/v1/ussd/jobs/next')
      .set(deviceAuth)
      .expect(200);

    assert.equal(res.body.status, 'RUNNING');
    assert.equal(res.body.network, 'SAFARICOM');
    assert.equal(res.body.customerMsisdn, '254712345678');
    assert.ok(res.body.workflow, 'the workflow must travel with the job');
    jobId = res.body.id;
  });

  await t.test('a second poll finds no job (lease prevents double delivery)', async () => {
    await request(app).get('/api/v1/ussd/jobs/next').set(deviceAuth).expect(204);
  });

  await t.test('success without a carrier receipt is rejected', async () => {
    const res = await request(app)
      .post(`/api/v1/ussd/jobs/${jobId}/result`)
      .set(deviceAuth)
      .send({ status: 'SUCCESS', steps: [] })
      .expect(422);
    assert.equal(res.body.code, 'receipt_required');
  });

  await t.test('a menu response parks the job for manual confirmation', async () => {
    const res = await request(app)
      .post(`/api/v1/ussd/jobs/${jobId}/result`)
      .set(deviceAuth)
      .send({
        status: 'AWAITING_MANUAL_CONFIRMATION',
        error: 'Carrier returned a menu',
        steps: [{ index: 0, label: 'Buy', request: '***', outcome: 'MENU', durationMs: 900 }]
      })
      .expect(200);

    assert.equal(res.body.transaction.purchaseStatus, 'AWAITING_MANUAL_CONFIRMATION');
    assert.equal(res.body.transaction.manualConfirmationRequired, true);
  });

  await t.test('the operator records the final outcome', async () => {
    const res = await request(app)
      .post(`/api/v1/ussd/jobs/${jobId}/manual-confirmation`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ outcome: 'SUCCESS', note: 'Carrier SMS confirmed', carrierReference: 'SMS-1' })
      .expect(200);

    assert.equal(res.body.job.status, 'SUCCESS');
    assert.equal(res.body.transaction.purchaseStatus, 'SUCCESS');
  });

  await t.test('a settled transaction cannot be confirmed twice', async () => {
    const res = await request(app)
      .post(`/api/v1/ussd/jobs/${jobId}/manual-confirmation`)
      .set('Authorization', `Bearer ${adminToken}`)
      .send({ outcome: 'FAILED' })
      .expect(409);
    assert.equal(res.body.code, 'job_finalised');
  });

  await t.test('dashboard summary reflects the paid sale', async () => {
    const res = await request(app)
      .get('/api/v1/dashboard/summary')
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);

    assert.equal(res.body.totalSalesCents, 10000);
    assert.equal(res.body.successfulTransactions, 1);
    assert.equal(res.body.activeBundles, 1);
  });

  await t.test('a USSD job cannot exist for an unpaid transaction', async () => {
    const res = await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .set('Idempotency-Key', 'idem-key-0003')
      .send({ customerMsisdn: '0722000111', network: 'SAFARICOM', bundleId })
      .expect(201);

    await assert.rejects(
      () => services.ussd.createJobForTransaction(res.body),
      /PAID/
    );
  });

  await t.test('a payment amount mismatch is refused', async () => {
    const txn = await request(app)
      .post('/api/v1/transactions')
      .set(deviceAuth)
      .set('Idempotency-Key', 'idem-key-0004')
      .send({ customerMsisdn: '0733000222', network: 'SAFARICOM', bundleId })
      .expect(201);

    const payment = (await repo.payments.listByTransaction(txn.body.id))[0];
    const result = await services.payments.settle({
      payment,
      resultCode: 0,
      resultDesc: 'ok',
      receipt: 'MISMATCH1',
      amount: 1,
      raw: { test: true }
    });

    assert.equal(result.mismatch, true);
    const reloaded = await request(app)
      .get(`/api/v1/transactions/${txn.body.id}`)
      .set(deviceAuth)
      .expect(200);
    assert.equal(reloaded.body.paymentStatus, 'FAILED');
  });

  await t.test('device credentials are hashed at rest', async () => {
    const stored = await repo.devices.findById(device.id);
    assert.notEqual(stored.apiKeyHash, 'device-key-for-tests-only');
    assert.match(stored.apiKeyHash, /^[a-f0-9]{64}$/);
  });

  await t.test('audit log captured the privileged actions', async () => {
    const res = await request(app)
      .get('/api/v1/admin/audit-logs')
      .set('Authorization', `Bearer ${adminToken}`)
      .expect(200);
    const actions = res.body.map((entry) => entry.action);
    assert.ok(actions.includes('auth.login'));
    assert.ok(actions.includes('payment.confirmed'));
    assert.ok(actions.includes('ussd.job_created'));
  });
});
