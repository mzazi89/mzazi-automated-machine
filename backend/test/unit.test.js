import test from 'node:test';
import assert from 'node:assert/strict';

import { normalizeMsisdn, isValidMsisdn, toDarajaMsisdn, maskMsisdn } from '../src/utils/msisdn.js';
import { toCents, toDarajaAmount, formatKes } from '../src/utils/money.js';
import { hashPassword, verifyPassword, sha256, timingSafeEquals, randomToken } from '../src/utils/crypto.js';
import { canTransition, assertTransition, PAYMENT_TRANSITIONS, PURCHASE_TRANSITIONS, mayRetryDelivery } from '../src/domain/stateMachine.js';
import { validateUssdTemplate, validateWorkflow, renderTemplate } from '../src/domain/workflowRules.js';
import { normalizeStkCallback } from '../src/services/paymentService.js';

test('MSISDN normalisation accepts every Kenyan format', () => {
  const expected = '254712345678';
  for (const input of ['0712345678', '712345678', '+254712345678', '254712345678', '0712 345 678']) {
    assert.equal(normalizeMsisdn(input), expected, `failed for ${input}`);
  }
  assert.equal(normalizeMsisdn('0110000000'), '254110000000');
});

test('MSISDN validation rejects junk and foreign numbers', () => {
  for (const input of ['', 'abc', '0712345', '0812345678', '255712345678', null, undefined]) {
    assert.equal(normalizeMsisdn(input), null, `should reject ${input}`);
  }
  assert.equal(isValidMsisdn('254712345678'), true);
  assert.equal(isValidMsisdn('254812345678'), false);
  assert.throws(() => toDarajaMsisdn('nope'));
});

test('MSISDN masking never leaks the full subscriber number', () => {
  const masked = maskMsisdn('254712345678');
  assert.equal(masked, '254712***78');
  assert.ok(!masked.includes('4567'));
});

test('money conversion is integer-safe', () => {
  assert.equal(toCents('100.55'), 10055);
  assert.equal(toCents(0), 0);
  assert.equal(toCents(-1), null);
  assert.equal(toDarajaAmount(10000), 100);
  assert.throws(() => toDarajaAmount(10050)); // half-shilling cannot be charged
  assert.match(formatKes(10000), /100\.00/);
});

test('password hashing round-trips and rejects the wrong password', async () => {
  const hash = await hashPassword('StrongPassword123!');
  assert.ok(hash.startsWith('scrypt$'));
  assert.equal(await verifyPassword('StrongPassword123!', hash), true);
  assert.equal(await verifyPassword('wrong-password', hash), false);
  assert.equal(await verifyPassword('StrongPassword123!', 'garbage'), false);
});

test('hashes and constant-time comparison behave', () => {
  assert.equal(sha256('abc').length, 64);
  assert.equal(timingSafeEquals('abc', 'abc'), true);
  assert.equal(timingSafeEquals('abc', 'abd'), false);
  assert.equal(timingSafeEquals('abc', 'abcd'), false);
  assert.ok(randomToken(16).length >= 20);
});

test('payment state machine forbids illegal jumps', () => {
  assert.equal(canTransition(PAYMENT_TRANSITIONS, 'PENDING', 'PAID'), true);
  assert.equal(canTransition(PAYMENT_TRANSITIONS, 'FAILED', 'PAID'), false);
  assert.equal(canTransition(PAYMENT_TRANSITIONS, 'PAID', 'PENDING'), false);
  assert.throws(() => assertTransition(PAYMENT_TRANSITIONS, 'FAILED', 'PAID', 'payment'));
});

test('purchase state machine is separate from payment', () => {
  assert.equal(canTransition(PURCHASE_TRANSITIONS, 'QUEUED', 'RUNNING'), true);
  assert.equal(canTransition(PURCHASE_TRANSITIONS, 'QUEUED', 'SUCCESS'), false);
  assert.equal(canTransition(PURCHASE_TRANSITIONS, 'SUCCESS', 'FAILED'), false);
  assert.equal(canTransition(PURCHASE_TRANSITIONS, 'RUNNING', 'AWAITING_MANUAL_CONFIRMATION'), true);
});

test('retry is allowed only for transport failures that never reached the carrier', () => {
  const base = { status: 'QUEUED', attemptCount: 1, maxAttempts: 3 };
  assert.equal(mayRetryDelivery({ ...base, failureCategory: 'NETWORK_ERROR' }), true);
  assert.equal(mayRetryDelivery({ ...base, failureCategory: 'CARRIER_REJECTED' }), false);
  assert.equal(mayRetryDelivery({ ...base, failureCategory: 'TIMEOUT' }), false);
  assert.equal(
    mayRetryDelivery({ status: 'QUEUED', attemptCount: 3, maxAttempts: 3, failureCategory: 'NETWORK_ERROR' }),
    false
  );
});

test('USSD template validation blocks malformed codes', () => {
  assert.equal(validateUssdTemplate('*544*{bundleSizeMb}*{msisdnLocal}#'), null);
  assert.ok(validateUssdTemplate('').includes('empty'));
  assert.ok(validateUssdTemplate('hello'));
  assert.ok(validateUssdTemplate('*544*{pin}#').includes('{pin}'));
  assert.ok(validateUssdTemplate('*544*1'));
});

test('workflow cannot be enabled without a verifier', () => {
  const problems = validateWorkflow({
    steps: [{ requestTemplate: '*100*{amount}#' }],
    successPattern: 'successful',
    verifiedBy: null,
    enabled: true
  });
  assert.ok(problems.some((p) => p.includes('verified')));

  const ok = validateWorkflow({
    steps: [{ requestTemplate: '*100*{amount}#' }],
    successPattern: 'successful',
    verifiedBy: 'Operator ref 1234',
    enabled: true
  });
  assert.deepEqual(ok, []);
});

test('template rendering strips non-USSD characters', () => {
  const rendered = renderTemplate('*100*{amount}#', { amount: '100; rm -rf /' });
  assert.equal(rendered, '*100*100#');
});

test('STK callback normalisation handles the real Daraja shape', () => {
  const payload = {
    Body: {
      stkCallback: {
        MerchantRequestID: '29115-34620561-1',
        CheckoutRequestID: 'ws_CO_191220191020363925',
        ResultCode: 0,
        ResultDesc: 'The service request is processed successfully.',
        CallbackMetadata: {
          Item: [
            { Name: 'Amount', Value: 100 },
            { Name: 'MpesaReceiptNumber', Value: 'NLJ7RT61SV' },
            { Name: 'TransactionDate', Value: 20191219102115 },
            { Name: 'PhoneNumber', Value: 254708374149 }
          ]
        }
      }
    }
  };

  const normalized = normalizeStkCallback(payload);
  assert.equal(normalized.checkoutRequestId, 'ws_CO_191220191020363925');
  assert.equal(normalized.resultCode, 0);
  assert.equal(normalized.amount, 100);
  assert.equal(normalized.receipt, 'NLJ7RT61SV');

  const failed = normalizeStkCallback({
    Body: { stkCallback: { CheckoutRequestID: 'x', ResultCode: 1032, ResultDesc: 'Cancelled' } }
  });
  assert.equal(failed.resultCode, 1032);
  assert.equal(failed.receipt, null);
});
