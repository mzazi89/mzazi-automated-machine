# Deployment & operations

## 1. Neon PostgreSQL

1. Create a project at <https://console.neon.tech> (PostgreSQL 16+).
2. Copy the **pooled** connection string:
   `postgresql://USER:PASSWORD@ep-xxx-pooler.REGION.aws.neon.tech/neondb?sslmode=require`
3. Run the schema and the first administrator:

```bash
cd backend
export DATABASE_URL='postgresql://...'
export BOOTSTRAP_ADMIN_EMAIL='admin@yourdomain.co.ke'
export BOOTSTRAP_ADMIN_PASSWORD='<16+ random characters>'
npm run migrate
npm run seed
```

`migrate` is idempotent (`CREATE TABLE IF NOT EXISTS`, `DROP TRIGGER IF EXISTS`) and
installs the trigger that refuses a USSD job for an unpaid transaction. `seed` does
nothing if an administrator already exists.

Keep `DB_POOL_MAX` low (3–5) when the API runs on serverless functions; Neon's pooler
does the heavy multiplexing.

## 2. Vercel (API)

The backend is a standard Express app exported from `backend/api/index.js`; `vercel.json`
rewrites every path to that function.

```bash
cd backend
npx vercel link          # project settings → Root Directory: backend
npx vercel env add DATABASE_URL production
npx vercel env add JWT_SECRET production
npx vercel env add NODE_ENV production          # production
npx vercel env add TEST_MODE production         # false
npx vercel env add PGSSL production             # require
npx vercel env add DB_POOL_MAX production       # 3
npx vercel env add MPESA_CONSUMER_KEY production
npx vercel env add MPESA_CONSUMER_SECRET production
npx vercel env add MPESA_SHORTCODE production
npx vercel env add MPESA_PASSKEY production
npx vercel env add MPESA_CALLBACK_URL production
npx vercel env add MPESA_CALLBACK_SECRET production
npx vercel --prod
```

Generate secrets with `openssl rand -base64 48`. After the first deploy:

```bash
curl https://<deployment>/api/v1/health
```

`TEST_MODE` **must** be `false` in production: simulation endpoints are then refused and
a missing Daraja configuration surfaces as a clear `422`, never a fake success.

Any other Node host works too (`npm start` behind HTTPS); only `api/index.js` is
Vercel-specific.

## 3. M-Pesa Daraja

1. Create an app on <https://developer.safaricom.co.ke>, note the consumer key/secret.
2. For Lipa na M-Pesa Online, obtain the shortcode and passkey (sandbox shortcode is
   `174379` with the published sandbox passkey).
3. Set `MPESA_ENVIRONMENT=production` (or `sandbox`), the shortcode, passkey and the
   callback URL `https://<deployment>/api/v1/payments/mpesa/callback/<CALLBACK_SECRET>`.
4. Whitelist the callback host if required, then place one real KES 1 test payment and
   confirm the transaction reaches `PAID` in `/api/v1/transactions/:id`.

The callback secret is a path segment because Safaricom cannot send custom headers.
Rotate it if the URL is ever logged by a third party.

## 4. Android build & signing

Debug:

```bash
cd android
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
```

Production signing — create a keystore once, keep it **outside** the repository:

```bash
keytool -genkeypair -v -keystore mzazi-release.jks -alias mzazi \
  -keyalg RSA -keysize 4096 -validity 10000
```

```bash
export MZAZI_KEYSTORE_PATH=/secure/path/mzazi-release.jks
export MZAZI_KEYSTORE_PASSWORD='...'
export MZAZI_KEY_ALIAS=mzazi
export MZAZI_KEY_PASSWORD='...'
./gradlew bundleRelease        # app/build/outputs/bundle/release/app-release.aab
./gradlew assembleRelease      # or a signed APK
```

Without those variables the release build is **unsigned** and must not be shipped.
Verify before uploading:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## 5. Terminal commissioning

1. Install the APK on the handset that holds the operator SIMs.
2. Grant `READ_PHONE_STATE` (SIM enumeration) and `CALL_PHONE` (USSD). Refusal is
   handled gracefully — the app still runs in simulation.
3. Sign in with an administrator account, then Settings → Backend → paste the
   `deviceId` / `deviceKey` issued by `/admin/devices`.
4. Turn **USSD simulation off** only after a verified workflow exists for each network.
5. Run one real KES 1 sale per network and confirm the SMS receipt matches the
   transaction's `receiptText` before enabling the counter.

## 6. Operations

- Health: `GET /api/v1/health`; database-dependent checks are exercised by
  `/api/v1/dashboard/summary` with an admin token.
- Audit: `GET /api/v1/admin/audit-logs` records logins, bundle/workflow edits, device
  issuance, payment confirmations and every delivery transition.
- Backup: Neon point-in-time restore; the API holds no state of its own.
- Rotate `JWT_SECRET` by deploying a new value — all sessions drop and terminals must
  re-authenticate.
