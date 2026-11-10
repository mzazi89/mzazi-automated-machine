# MZAZI TECH AUTOMATED MACHINE

Automated multi-network data-bundle vending machine for **Safaricom**, **Airtel** and
**Telkom Kenya**, operated from an Android handset with verified USSD workflows and
M-Pesa (Daraja) payments verified server-side.

```
android/    Kotlin · Jetpack Compose · Material 3 · Room · WorkManager · MVVM
backend/    Node.js · Express · Neon PostgreSQL · Daraja STK Push · USSD job queue
```

Company: **MZAZI TECH INC** · Package: `com.mzazitech.automatedmachine`

---

## Non-negotiables

1. **No USSD codes ship with this project.** Every operator flow is entered by an
   administrator and marked with the person/operator reference that verified it. A
   workflow without a verifier cannot be enabled.
2. **Android cannot type into a live USSD session.** The engine sends one request at a
   time; when a carrier returns a menu it stops and hands the terminal to a human for
   confirmation instead of guessing menu digits.
3. **A delivery job is only created for a `PAID` transaction**, enforced in the service
   layer *and* by a PostgreSQL trigger. An SMS on the phone is never proof of payment.
4. **Duplicate purchases are blocked twice**: an idempotency key in Room/Postgres and a
   duplicate-window check before a second sale for the same number and bundle.

## Quick start (backend)

```bash
cd backend
cp .env.example .env          # fill DATABASE_URL, JWT_SECRET, MPESA_*
npm install
npm run migrate               # applies src/db/schema.sql (idempotent)
npm run seed                  # creates the first super_admin from env
npm start                     # http://localhost:8080/api/v1/health
npm test                      # 44 unit + API tests, no database required
```

`TEST_MODE=true` allows `/api/v1/payments/test/simulate` so the whole
purchase → payment → delivery chain can be rehearsed without money. **It must be
`false` in production**; the config validator refuses to boot otherwise.

## Quick start (Android)

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # engine, template, MSISDN and money tests
```

Point the app at your API with `-PmzaziApiBaseUrl=https://your-api.example/`.
Release signing reads `MZAZI_KEYSTORE_*` environment variables or an ignored
`keystore.properties`; with no credentials Gradle produces an **unsigned** release,
which is not a shippable artefact.

## Deploying the API to Vercel

`backend/api/index.js` exports the same Express app, and `vercel.json` rewrites all
paths to it. Set these environment variables in the Vercel project (never in git):

| Variable | Value |
| --- | --- |
| `DATABASE_URL` | Neon pooled connection string (`sslmode=require`) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `NODE_ENV` | `production` |
| `TEST_MODE` | `false` |
| `PGSSL` | `require` |
| `DB_POOL_MAX` | `3` (serverless) |
| `MPESA_CONSUMER_KEY` / `_SECRET` | Daraja app credentials |
| `MPESA_SHORTCODE` / `MPESA_PASSKEY` | Daraja paybill + passkey |
| `MPESA_CALLBACK_URL` | `https://<deployment>/api/v1/payments/mpesa/callback` |
| `MPESA_CALLBACK_SECRET` | long random path token |

Then `npm run migrate` once against the Neon URL, and deploy.

## Pairing an Android terminal

```bash
curl -X POST https://<deployment>/api/v1/admin/devices \
  -H "Authorization: Bearer <admin token>" \
  -H "Content-Type: application/json" \
  -d '{"label":"Counter 1"}'
```

The response contains `deviceId` and `deviceKey` **once**. Enter both in
Settings → Backend → Configure device credentials; they are stored with a
Keystore-backed AES-GCM key, and only the SHA-256 hash is kept server-side.

## Documentation

- [`docs/API.md`](docs/API.md) — endpoints, auth, state machines
- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) — Neon, Vercel, signing, operations
- [`docs/SECURITY.md`](docs/SECURITY.md) — threat model and guarantees

## Licence / ownership

Proprietary software of MZAZI TECH INC. No secrets, private keys or real payment
credentials are stored in this repository.
