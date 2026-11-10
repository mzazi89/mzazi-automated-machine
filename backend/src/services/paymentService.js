import { badRequest, conflict, forbidden, notFound, unprocessable } from '../utils/errors.js';
import { assertTransition, PAYMENT_TRANSITIONS } from '../domain/stateMachine.js';
import { toDarajaAmount } from '../utils/money.js';
import { toDarajaMsisdn } from '../utils/msisdn.js';
import { logger } from '../utils/logger.js';

/**
 * Normalises the Daraja STK callback body into a flat object we control.
 * Safaricom sends `Body.stkCallback.CallbackMetadata.Item[]` with name/value pairs.
 */
export function normalizeStkCallback(body) {
  const callback = body?.Body?.stkCallback ?? body?.stkCallback ?? body;
  if (!callback) return null;

  const items = callback?.CallbackMetadata?.Item ?? [];
  const metadata = {};
  for (const item of items) {
    if (item?.Name) metadata[item.Name] = item.Value;
  }

  return {
    merchantRequestId: callback.MerchantRequestID ?? null,
    checkoutRequestId: callback.CheckoutRequestID ?? null,
    resultCode: Number(callback.ResultCode),
    resultDesc: callback.ResultDesc ?? '',
    amount: metadata.Amount !== undefined ? Number(metadata.Amount) : null,
    receipt: metadata.MpesaReceiptNumber ?? null,
    phone: metadata.PhoneNumber !== undefined ? String(metadata.PhoneNumber) : null,
    transactionDate: metadata.TransactionDate ?? null,
    raw: body
  };
}

/**
 * Payment lifecycle. The callback is the only thing that can mark money as received.
 * An Android SMS is never accepted as proof of payment by this API.
 */
export function createPaymentService({ repo, config, daraja, ussd, audit, tills }) {
  async function initiateStkPush({ transaction, phone }) {
    if (transaction.paymentStatus !== 'PENDING') {
      throw conflict('This transaction is not awaiting payment', 'not_pending');
    }
    if (!phone) throw badRequest('A phone number is required for the STK push');

    const amount = toDarajaAmount(transaction.amountCents);
    const msisdn = toDarajaMsisdn(phone);

    // Payments must land on the till the owner connected for this network.
    const credentials = await tills?.credentialsForNetwork(transaction.network);

    let response;
    try {
      response = await daraja.stkPush({
        phone: msisdn,
        amount,
        accountReference: transaction.reference,
        description: `MZAZI ${transaction.bundleName}`.slice(0, 100),
        credentials
      });
    } catch (error) {
      logger.error('STK push failed', { transactionId: transaction.id, error: error.message });
      await repo.transactions.update(transaction.id, {
        paymentStatus: 'FAILED',
        failureReason: 'Could not start the M-Pesa request'
      });
      await audit?.record({
        actorType: 'system',
        action: 'payment.stk_push_failed',
        target: transaction.id,
        detail: { error: error.message }
      });
      throw unprocessable(
        'M-Pesa could not start the payment request. Try again shortly.',
        'stk_push_failed'
      );
    }

    const payment = await repo.payments.create({
      transactionId: transaction.id,
      provider: 'MPESA',
      checkoutRequestId: response.CheckoutRequestID ?? null,
      merchantRequestId: response.MerchantRequestID ?? null,
      amountCents: transaction.amountCents,
      phone: msisdn,
      status: 'PENDING',
      rawRequest: response
    });

    const updated = await repo.transactions.update(transaction.id, {
      checkoutRequestId: response.CheckoutRequestID ?? null,
      paymentReference: response.CheckoutRequestID ?? null
    });

    await audit?.record({
      actorType: 'system',
      action: 'payment.stk_push_initiated',
      target: transaction.id,
      detail: {
        checkoutRequestId: response.CheckoutRequestID ?? null,
        simulated: Boolean(response.simulated)
      }
    });

    return { payment, transaction: updated, simulated: Boolean(response.simulated) };
  }

  async function findPaymentForCallback(checkoutRequestId) {
    if (!checkoutRequestId) return null;
    return repo.payments.findByCheckoutId(checkoutRequestId);
  }

  /**
   * Applies the verified outcome of an STK request exactly once.
   * Duplicate callbacks (Daraja retries) are acknowledged but never re-applied.
   */
  async function settle({ payment, resultCode, resultDesc, receipt, amount, raw }) {
    if (payment.status === 'SUCCESS' || payment.status === 'FAILED') {
      return { duplicate: true, payment };
    }

    const transaction = await repo.transactions.findById(payment.transactionId);
    if (!transaction) throw notFound('Transaction for this payment no longer exists');

    const success = Number(resultCode) === 0;

    if (success) {
      const expected = payment.amountCents / 100;
      if (amount !== null && Number(amount) !== expected) {
        await repo.payments.update(payment.id, {
          status: 'FAILED',
          resultCode,
          resultDesc: 'Amount mismatch in callback',
          rawCallback: raw
        });
        await repo.transactions.update(transaction.id, {
          paymentStatus: 'FAILED',
          failureReason: 'Payment amount did not match the order'
        });
        await audit?.record({
          actorType: 'mpesa',
          action: 'payment.amount_mismatch',
          target: payment.id,
          detail: { expected, received: amount }
        });
        return { duplicate: false, mismatch: true };
      }
      if (!receipt) {
        throw unprocessable('A successful callback must include an M-Pesa receipt');
      }

      assertTransition(PAYMENT_TRANSITIONS, transaction.paymentStatus, 'PAID', 'payment');

      const updatedPayment = await repo.payments.update(payment.id, {
        status: 'SUCCESS',
        resultCode,
        resultDesc,
        mpesaReceipt: receipt,
        rawCallback: raw
      });
      const updatedTransaction = await repo.transactions.update(transaction.id, {
        paymentStatus: 'PAID',
        mpesaReceipt: receipt,
        paymentReference: receipt,
        paidAt: new Date().toISOString(),
        failureReason: null
      });

      // Delivery is only ever queued after this point.
      await ussd.createJobForTransaction(updatedTransaction);

      await audit?.record({
        actorType: 'mpesa',
        action: 'payment.confirmed',
        target: payment.id,
        detail: { transactionId: transaction.id, receipt }
      });

      return { duplicate: false, payment: updatedPayment, transaction: updatedTransaction };
    }

    assertTransition(PAYMENT_TRANSITIONS, transaction.paymentStatus, 'FAILED', 'payment');
    const updatedPayment = await repo.payments.update(payment.id, {
      status: 'FAILED',
      resultCode,
      resultDesc,
      rawCallback: raw
    });
    const updatedTransaction = await repo.transactions.update(transaction.id, {
      paymentStatus: 'FAILED',
      purchaseStatus: 'FAILED',
      failureReason: resultDesc || 'Payment was not completed'
    });
    await audit?.record({
      actorType: 'mpesa',
      action: 'payment.failed',
      target: payment.id,
      detail: { resultCode, resultDesc }
    });
    return { duplicate: false, payment: updatedPayment, transaction: updatedTransaction };
  }

  async function handleCallback(rawBody) {
    const callback = normalizeStkCallback(rawBody);
    if (!callback || !callback.checkoutRequestId) {
      throw badRequest('Malformed M-Pesa callback payload', 'malformed_callback');
    }

    const payment = await findPaymentForCallback(callback.checkoutRequestId);
    if (!payment) {
      // Acknowledge unknown references so Daraja stops retrying, but record the event.
      await audit?.record({
        actorType: 'mpesa',
        action: 'payment.callback_unknown_reference',
        target: callback.checkoutRequestId,
        detail: { resultCode: callback.resultCode }
      });
      return { ignored: true, reason: 'unknown_checkout_request_id' };
    }

    if (config.mpesa.verifyWithQuery && daraja.isConfigured && !config.testMode && callback.resultCode === 0) {
      try {
        const status = await daraja.queryStatus(callback.checkoutRequestId);
        if (status.ResultCode !== undefined && Number(status.ResultCode) !== 0) {
          logger.warn('Callback contradicted by status query', {
            checkoutRequestId: callback.checkoutRequestId,
            queryResultCode: status.ResultCode
          });
          return settle({
            payment,
            resultCode: Number(status.ResultCode),
            resultDesc: status.ResponseDescription ?? 'Status query rejected the payment',
            receipt: null,
            amount: null,
            raw: rawBody
          });
        }
      } catch (error) {
        // Query failure must not block a genuine callback; it is logged for review.
        logger.warn('Status verification unavailable', { error: error.message });
      }
    }

    return settle({
      payment,
      resultCode: callback.resultCode,
      resultDesc: callback.resultDesc,
      receipt: callback.receipt,
      amount: callback.amount,
      raw: rawBody
    });
  }

  /** TEST MODE ONLY — settles a transaction without money moving. */
  async function simulate({ transactionId, outcome = 'SUCCESS' }) {
    if (!config.testMode) {
      throw forbidden('Payment simulation is disabled', 'test_mode_disabled');
    }
    const transaction = await repo.transactions.findById(transactionId);
    if (!transaction) throw notFound('Transaction not found');

    let payment = (await repo.payments.listByTransaction(transaction.id))[0];
    if (!payment) {
      payment = await repo.payments.create({
        transactionId: transaction.id,
        provider: 'MPESA',
        checkoutRequestId: `ws_CO_TEST_${transaction.id.slice(0, 8)}`,
        merchantRequestId: `test-${transaction.id.slice(0, 8)}`,
        amountCents: transaction.amountCents,
        phone: transaction.customerMsisdn,
        status: 'PENDING',
        rawRequest: { simulated: true }
      });
      await repo.transactions.update(transaction.id, {
        checkoutRequestId: payment.checkoutRequestId
      });
    }

    const success = String(outcome).toUpperCase() === 'SUCCESS';
    return settle({
      payment,
      resultCode: success ? 0 : 1032,
      resultDesc: success ? 'The service request is processed successfully.' : 'Request cancelled by user',
      receipt: success ? `TEST${transaction.id.replace(/-/g, '').slice(0, 8).toUpperCase()}` : null,
      amount: transaction.amountCents / 100,
      raw: { simulated: true, outcome }
    });
  }

  async function statusFor(transactionId) {
    const transaction = await repo.transactions.findById(transactionId);
    if (!transaction) throw notFound('Transaction not found');
    const payments = await repo.payments.listByTransaction(transactionId);
    return {
      transactionId,
      paymentStatus: transaction.paymentStatus,
      purchaseStatus: transaction.purchaseStatus,
      mpesaReceipt: transaction.mpesaReceipt,
      failureReason: transaction.failureReason,
      payments: payments.map((p) => ({
        checkoutRequestId: p.checkoutRequestId,
        status: p.status,
        resultCode: p.resultCode,
        resultDesc: p.resultDesc
      }))
    };
  }

  return { initiateStkPush, handleCallback, simulate, statusFor, settle };
}
