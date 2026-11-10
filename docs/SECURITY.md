# Security model

## Secrets

Nothing sensitive is in this repository. `.env`, `keystore.properties`, `*.jks` and
`local.properties` are ignored; production values live in Vercel environment variables
and the signing key stays outside the tree.

- Passwords: scrypt (`N=16384, r=8, p=1`), per-password salt, constant-time compare.
- Administrator sessions: HS256 JWT, `type` claim separates access from refresh so an
  access token cannot be replayed at `/auth/refresh`.
- Device credentials: 32-byte random key, shown once, stored as SHA-256; comparison is
  constant-time. Revoking a device disables it immediately.
- Android: bearer token and device key live in a Keystore-backed AES-256-GCM blob, not
  in Room and not in plain SharedPreferences. Release builds log no headers or bodies.

## Payment integrity

- The only thing that can mark money as received is the Daraja callback, matched to a
  known `CheckoutRequestID`; unknown references are acknowledged and discarded.
- Callbacks are protected by a secret path segment (Safaricom cannot send headers).
- Amount mismatches fail the payment, and a duplicate callback is acknowledged without
  re-applying it.
- Optional status-query cross-check (`MPESA_VERIFY_WITH_QUERY`) before trusting a
  success callback.
- An Android SMS is never accepted as proof of payment, anywhere in the stack.

## Delivery integrity

- A USSD job requires `payment_status = 'PAID'`: enforced in `paymentService`, in
  `ussdService`, and by the `trg_ussd_jobs_require_paid` trigger, so no code path —
  including a future one — can queue an unpaid delivery.
- Delivery is claimed with `FOR UPDATE SKIP LOCKED` plus a 120-second lease, so two
  terminals cannot execute the same purchase.
- A USSD purchase is not idempotent at the carrier: retries happen only for
  `NETWORK_ERROR` (request never reached the application layer) and only while attempts
  remain. Carrier rejections, timeouts and menus are terminal or human-confirmed.
- `SUCCESS` without a carrier receipt is rejected with `422`.
- Android cannot type into a live USSD session; the app hands over to a human rather
  than guessing menu digits.

## Operator codes

No USSD code ships with this project. A workflow must be created by an administrator,
pass template validation, and carry a `verifiedBy` reference before it can be enabled.
Simulation-only workflows are marked `MZAZI-TEST-ONLY` and the engine refuses to run
them when the transport is a real SIM.

## Transport & limits

- HTTPS enforced: `MPESA_CALLBACK_URL` and `PUBLIC_BASE_URL` must be HTTPS in
  production; Android sets `cleartextTrafficPermitted="false"`.
- Helmet headers, JSON body cap of 256 kB, global 300 req/min limit, 10 attempts per
  15 minutes on login.
- Every privileged action is written to `audit_logs` with actor, target and request IP.

## Operational hygiene

Rotate `JWT_SECRET` to invalidate all sessions. Rotate device keys by revoking and
reissuing. Rotate `MPESA_CALLBACK_SECRET` if the callback URL is ever exposed in logs.
Neon provides point-in-time restore; the API keeps no state of its own.
