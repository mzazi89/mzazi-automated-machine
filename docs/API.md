# API reference — MZAZI AUTOMATED MACHINE

Base path: `/api/v1` · JSON only · all timestamps ISO-8601 unless stated.

## Authentication

| Principal | Headers |
| --- | --- |
| Administrator | `Authorization: Bearer <accessToken>` |
| Android terminal | `X-Device-Id: <uuid>` + `X-Device-Key: <key>` |

Access tokens live 12 h (`JWT_ACCESS_TTL`), refresh tokens 30 d. Device keys are stored
only as SHA-256 hashes and are shown once at creation.

Every response carries `X-Request-Id`; errors return
`{ "error", "message", "code", "details?", "requestId" }`.

---

## Auth

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| POST | `/auth/login` | public, 10/15 min | `{email,password}` → `{accessToken,refreshToken,admin}` |
| POST | `/auth/refresh` | public | `{refreshToken}` → new pair |
| GET | `/auth/me` | admin | current identity |

## Catalogue

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| GET | `/bundles?network=&includeDisabled=` | admin or device | enabled bundles only for devices |
| POST | `/bundles` | admin | `{network,name,dataSizeMb,sellingPriceCents,costPriceCents?,validityDays?,workflowId?}` |
| PATCH | `/bundles/:id` | admin | partial update |
| DELETE | `/bundles/:id` | admin | soft delete |
| GET | `/workflows?network=` | admin or device | includes steps |
| POST | `/workflows` | admin | rejected unless templates are dialable and `verifiedBy` is set when `enabled` |
| PATCH | `/workflows/:id` | admin | bumps `version` |

## Transactions

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| POST | `/transactions` | admin or device | **requires `Idempotency-Key`**; `{customerMsisdn,network,bundleId,simSlot?,testMode?}` |
| GET | `/transactions?limit=&offset=&status=` | admin or device | newest first |
| GET | `/transactions/:id` | admin or device | |
| POST | `/transactions/:id/cancel` | admin or device | refused once `PAID` |
| GET | `/dashboard/summary` | admin or device | totals, today's revenue, counts |

`201` on create, `200` with `idempotentReplay: true` when the same key is replayed.
`409 duplicate_active_purchase` when an active sale already exists for that
number + bundle inside `DUPLICATE_WINDOW_MINUTES`.

## Payments

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| POST | `/payments/mpesa/stk-push` | admin or device | `{transactionId, phone?}` |
| POST | `/payments/mpesa/callback/:secret` | secret path | Daraja STK callback |
| POST | `/payments/mpesa/timeout` | public | logged only |
| GET | `/payments/:transactionId/status` | admin or device | transaction + attempt list |
| POST | `/payments/test/simulate` | admin or device, **TEST_MODE only** | `{transactionId,outcome}` |

Callback rules: unknown `CheckoutRequestID` is acknowledged but ignored; a replay is
acknowledged with `duplicate: true`; an amount mismatch marks the payment `FAILED` and
never delivers. When `MPESA_VERIFY_WITH_QUERY=true` the payment is cross-checked with the
status-query API before being trusted.

## USSD delivery

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| GET | `/ussd/jobs/next` | admin or device | leases one job, `204` when none |
| POST | `/ussd/jobs/:id/result` | admin or device | `{status,steps,error,receiptText,failureCategory,retryable}` |
| POST | `/ussd/jobs/:id/manual-confirmation` | admin or device | `{outcome,note?,carrierReference?}` |
| GET | `/ussd/jobs?status=&limit=` | admin | queue inspection |

- `SUCCESS` without `receiptText` → `422 receipt_required`.
- `AWAITING_MANUAL_CONFIRMATION` parks the transaction for a human.
- `FAILED` is retried **only** when `failureCategory=NETWORK_ERROR` and attempts remain;
  a USSD purchase is not idempotent at the carrier, so nothing else is ever resent.

## Admin

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| GET | `/admin/devices` | admin | |
| POST | `/admin/devices` | admin `admin`/`super_admin` | returns `deviceId` + `deviceKey` once |
| POST | `/admin/devices/:id/revoke` | admin | disables the terminal |
| GET | `/admin/audit-logs?limit=` | admin | |
| GET | `/admin/config` | admin | non-secret runtime flags |
| GET | `/health` | public | liveness |

## State machines

```
payment:  PENDING → PAID | FAILED | CANCELLED ;  PAID → REVERSED | REFUNDED
purchase: QUEUED → RUNNING | CANCELLED | FAILED
          RUNNING → SUCCESS | FAILED | AWAITING_MANUAL_CONFIRMATION
          AWAITING_MANUAL_CONFIRMATION → SUCCESS | FAILED | CANCELLED
          SUCCESS / FAILED / CANCELLED are terminal
```

An illegal transition returns `409 illegal_transition` rather than silently rewriting
history.
