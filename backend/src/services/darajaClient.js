import crypto from 'node:crypto';
import { logger } from '../utils/logger.js';

const SANDBOX = 'https://sandbox.safaricom.co.ke';
const PRODUCTION = 'https://api.safaricom.co.ke';

/**
 * Safaricom Daraja (M-Pesa) client.
 *
 * - OAuth token is cached until shortly before expiry.
 * - STK password is generated per request as required by Daraja:
 *   base64(Shortcode + Passkey + Timestamp).
 * - When credentials are absent, and only when TEST_MODE is on, a deterministic mock
 *   response is returned so the whole flow can be rehearsed without money.
 */
export function createDarajaClient({ config, fetchImpl = globalThis.fetch }) {
  const baseUrl = config.mpesa.environment === 'production' ? PRODUCTION : SANDBOX;

  let cachedToken = null;
  let tokenExpiresAt = 0;

  const isConfigured = Boolean(
    config.mpesa.consumerKey &&
      config.mpesa.consumerSecret &&
      config.mpesa.shortcode &&
      config.mpesa.passkey
  );

  function timestamp() {
    const now = new Date();
    const pad = (value) => String(value).padStart(2, '0');
    return (
      String(now.getFullYear()) +
      pad(now.getMonth() + 1) +
      pad(now.getDate()) +
      pad(now.getHours()) +
      pad(now.getMinutes()) +
      pad(now.getSeconds())
    );
  }

  async function accessToken() {
    if (cachedToken && Date.now() < tokenExpiresAt - 60_000) return cachedToken;

    const credentials = Buffer.from(
      `${config.mpesa.consumerKey}:${config.mpesa.consumerSecret}`
    ).toString('base64');

    const response = await fetchImpl(
      `${baseUrl}/oauth/v1/generate?grant_type=client_credentials`,
      { method: 'GET', headers: { Authorization: `Basic ${credentials}` } }
    );

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`Daraja OAuth failed (${response.status}): ${body.slice(0, 200)}`);
    }

    const data = await response.json();
    cachedToken = data.access_token;
    tokenExpiresAt = Date.now() + Number(data.expires_in ?? 3599) * 1000;
    return cachedToken;
  }

  /**
   * Initiates an STK push. Returns Daraja's raw payload plus a normalised shape.
   */
  async function stkPush({ phone, amount, accountReference, description, credentials }) {
    if (!isConfigured) {
      if (!config.testMode) {
        throw new Error('M-Pesa is not configured. Set MPESA_* variables before taking payments.');
      }
      logger.warn('Daraja not configured — returning simulated STK push (TEST_MODE only)');
      return {
        simulated: true,
        CheckoutRequestID: `ws_CO_TEST_${crypto.randomUUID().replace(/-/g, '').slice(0, 20)}`,
        MerchantRequestID: `test-${crypto.randomUUID()}`,
        ResponseCode: '0',
        ResponseDescription: 'Success. Request accepted for processing (simulated)',
        CustomerMessage: 'Simulated STK push: enter M-Pesa PIN (test mode)'
      };
    }

    const token = await accessToken();
    const ts = timestamp();
    // A till configured by the owner wins over the bootstrap env credentials.
    const shortcode = credentials?.shortcode ?? config.mpesa.shortcode;
    const passkey = credentials?.passkey ?? config.mpesa.passkey;
    const password = Buffer.from(`${shortcode}${passkey}${ts}`).toString('base64');

    const payload = {
      BusinessShortCode: shortcode,
      Password: password,
      Timestamp: ts,
      TransactionType: 'CustomerPayBillOnline',
      Amount: amount,
      PartyA: phone,
      PartyB: shortcode,
      PhoneNumber: phone,
      CallBackURL: config.mpesa.callbackUrl,
      AccountReference: accountReference.slice(0, 12),
      TransactionDesc: description.slice(0, 100)
    };

    const response = await fetchImpl(`${baseUrl}/mpesa/stkpush/v1/processrequest`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(
        `Daraja STK push failed (${response.status}): ${JSON.stringify(data).slice(0, 300)}`
      );
    }
    return { ...data, simulated: false };
  }

  /** Queries the final state of an STK request. Used to double-check callbacks. */
  async function queryStatus(checkoutRequestId) {
    if (!isConfigured) {
      return { simulated: true, ResultCode: '0', ResponseCode: '0' };
    }
    const token = await accessToken();
    const ts = timestamp();
    // A till configured by the owner wins over the bootstrap env credentials.
    const shortcode = credentials?.shortcode ?? config.mpesa.shortcode;
    const passkey = credentials?.passkey ?? config.mpesa.passkey;
    const password = Buffer.from(`${shortcode}${passkey}${ts}`).toString('base64');

    const response = await fetchImpl(`${baseUrl}/mpesa/stkpushquery/v1/query`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        BusinessShortCode: shortcode,
        Password: password,
        Timestamp: ts,
        CheckoutRequestID: checkoutRequestId
      })
    });
    const data = await response.json().catch(() => ({}));
    return { ...data, simulated: false };
  }

  return { stkPush, queryStatus, accessToken, isConfigured, timestamp };
}
