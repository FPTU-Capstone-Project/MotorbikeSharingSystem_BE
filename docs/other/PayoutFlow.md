## Wallet Payout Flow (Automatic vs Manual)

This document captures the end-to-end withdrawal lifecycle, the exact endpoints, involved services, and data artifacts for both AUTOMATIC (PayOS) and MANUAL payout modes.

---

### 1. Domain Objects & Terminology

| Term | Description | Where stored |
|------|-------------|--------------|
| `payoutRef` | External reference ID shared with PayOS | `Transaction.pspRef` |
| `idempotencyKey` | Uniquely identifies a payout run to prevent duplicates | `Transaction.idempotencyKey` |
| `groupId` | Links user + ledger transactions | `Transaction.groupId` |
| `mode:AUTOMATIC/MANUAL` | Embedded in transaction note (`note="...| mode:AUTOMATIC | ..."`) | Transaction note |
| `PayoutOrderRequest` | Internal DTO used when calling PayOS | `com.mssus.app.dto.request.PayoutOrderRequest` |
| `PayoutWebhookRequest` | Payload parsed from PayOS webhook | `com.mssus.app.dto.request.wallet.PayoutWebhookRequest` |

---

### 2. Rider/User Initiation (`POST /api/v1/wallet/payout/init`)

1. **Input contract**
   ```json
   {
     "amount": 250000,
     "bankName": "VPBank",
     "bankBin": "970436",
     "accountNumber": "1027107637",
     "accountHolder": "Nguyen Van A",
     "mode": "AUTOMATIC"   // or MANUAL (optional, default AUTOMATIC)
   }
   ```
2. **Validations**
   - Wallet balance >= amount + fees.
   - Amount respects min/max thresholds.
   - Daily/weekly withdrawal quotas.
3. **Side effects**
   - Create SSOT transactions (credit/debit) with `status=PENDING`.
   - Persist payout metadata inside transaction notes:
     ```
     bankName:VPBank | bankBin:970436 | bankAccountNumber:1027107637 | accountHolderName:Nguyen Van A | mode:AUTOMATIC | description:Payout to VPBank - ****7637
     ```
   - Return payload:
     ```json
     {
       "payoutRef": "PAYOUT-1763978349527",
       "idempotencyKey": "9d3c8f0e-..." ,
       "status": "PENDING"
     }
     ```

---

### 3. Admin Moves to PROCESSING (`PUT /api/v1/wallet/payout/{payoutRef}/process`)

- Requires `ROLE_ADMIN`.
- Loads all transactions with the given `pspRef`.
- Updates `status=PROCESSING`, appends
  ```
  processedBy:admin@mssus.com | processedAt:2024-11-24T11:10:10
  ```
  to every transaction note.
- Detects payout mode from notes and branches:
  - `AUTOMATIC` → PayOS flow (section 4).
  - `MANUAL` → manual banking flow (section 5).

---

### 4. AUTOMATIC Mode (PayOS Integration)

#### 4.1 Build request & signature
- DTO: `PayoutOrderRequest`
  ```
  referenceId = payoutRef
  amount      = amount in VND (long)
  description = "Payout"
  toBin       = bankBin
  toAccountNumber = bankAccountNumber
  category    = ["payout"] (list preserved for JSON body)
  ```
- Canonical payload for signature (sorted keys, values URL-encoded, null→""):
  ```
  amount=2000&category=payout&description=Payout&referenceId=PAYOUT-...&toAccountNumber=1027107637&toBin=970436
  ```
- Signature = `HMAC_SHA256(payload, payos.payout.checksum-key)`

#### 4.2 Create payout order
- **HTTP**: `POST https://api-merchant.payos.vn/v1/payouts`
- **Headers**:
  | Header | Value |
  |--------|-------|
  | `x-client-id` | `payos.payout.client-id` |
  | `x-api-key` | `payos.payout.api-key` |
  | `x-idempotency-key` | same as local idempotencyKey |
  | `x-signature` | from step 4.1 |
- **Body** (JSON):
  ```json
  {
    "referenceId": "PAYOUT-1763978349527",
    "amount": 2000,
    "description": "Payout",
    "toBin": "970436",
    "toAccountNumber": "1027107637",
    "category": ["payout"]
  }
  ```
- **Retries**: 3 attempts, exponential backoff, only on network/5xx errors.

#### 4.3 Persist response
- Parse `code`, `desc`, `data.transactionId`, `data.status`.
- Append to each transaction note:
  ```
  payos_code:20 | payos_desc:PROCESSING | payos_txn_id:123456789
  ```
- Status mapping:
  | PayOS status/code | Local status |
  |-------------------|--------------|
  | `00`, `SUCCESS`, `COMPLETED` | `SUCCESS` |
  | `PROCESSING`, `PENDING` | `PROCESSING` |
  | `FAILED`, any other code | `FAILED` |

#### 4.4 Notifications & cache
- `BalanceCalculationService.invalidateBalanceCache(walletId)`
- `PayoutNotificationService`:
  - `notifyPayoutSuccess(actorUser, payoutRef, amount)`
  - `notifyPayoutProcessing(...)`
  - `notifyPayoutFailed(..., reason)`

#### 4.5 Webhook ingestion
- **Endpoint:** `POST /api/v1/payos/payout/webhook`
- Steps:
  1. Deserialize JSON → `PayoutWebhookRequest`.
  2. If HTTP header `x-signature` present, override request signature field.
  3. Verify signature & referenceId.
  4. Update transactions + wallet caches + notifications similar to step 4.3.

#### 4.6 Polling fallback
- **Scheduler:** `PayoutPollingScheduler` (cron defined in config).
- **Service:** `PayoutPollingServiceImpl#pollPayoutStatus`
- Criteria:
  - Transaction type = `PAYOUT`.
  - Status in `{PENDING, PROCESSING}`.
  - Note contains `mode:AUTOMATIC`.
  - Age between `min-age-minutes` and `max-age-hours`.
- Uses `PayOSPayoutClient#getPayoutStatusByRef`.
- Appends log:
  ```
  polled_at:2024-11-24T12:44:00 | payos_code:00 | payos_status:SUCCESS | payos_txn_id:123
  ```

#### 4.7 Operational APIs for reconciliation
| Endpoint | Purpose | Notes |
|----------|---------|-------|
| `GET /api/v1/payos/payouts` | Paginated PayOS payouts | Query params mapped to PayOS `limit`, `offset`, `approvalState`, `category`, `fromDate`, `toDate` (seconds precision). |
| `GET /api/v1/payos/payouts/{payoutId}` | One PayOS payout detail | Calls `/v1/payouts/{id}`. |
| `GET /api/v1/payos/payouts-account/balance` | PayOS payout account balance | Useful for reconciliation dashboards. |

---

### 5. MANUAL Mode (No PayOS Call)

#### 5.1 When to use
- Bank is temporary unsupported by PayOS.
- Admin needs to override payout amount/description.
- Compliance requires review before disbursing funds.

#### 5.2 Process
1. **Processing step** (same endpoint as automatic). Mode detection prevents PayOS API call.
2. **Manual transfer**
   - Admin transfers funds via banking portal/cash office.
   - Collects evidence: screenshot, PDF receipt, bank reference number.
3. **Completion endpoint**
   - `PUT /api/v1/wallet/payout/{payoutRef}/complete`
   - Multipart form:
     ```
     evidenceFile: <binary> (required)
     notes: "Transferred via Vietcombank, reference FT123456"
     ```
   - `WalletService.completePayout`:
     - Validates file present.
     - Persists evidence path + notes.
     - Sets status `SUCCESS`, invalidates cache, sends success notification.
4. **Failure endpoint**
   - `PUT /api/v1/wallet/payout/{payoutRef}/fail`
   - Form field `reason` required.
   - Sets status `FAILED`, logs reason in notes, notifies user.
5. **Audit trail**
   - Notes contain: `processedBy`, `processedAt`, `manual_ref:FT123456`, `evidence_path:s3://...`.
   - Evidence file stored in configured storage bucket; link only visible to admins.

#### 5.3 Notifications
- Use same notification service; message template mentions “manual review”.
  - Processing: “Your withdrawal is being processed manually…”
  - Success: “Manual payout completed … evidence attached.”
  - Failure: includes `reason`.

---

### 6. Shared Lifecycle Overview

```
User -> /wallet/payout/init -> Transactions (PENDING)
Admin -> /wallet/payout/{ref}/process -> Transactions (PROCESSING)
  ├─ Mode AUTOMATIC -> PayOS API + webhook + polling -> Success/Fail
  └─ Mode MANUAL    -> manual transfer -> /complete or /fail
```

| Step | Automatic Mode | Manual Mode |
|------|----------------|-------------|
| Initiation | `/wallet/payout/init` | `/wallet/payout/init` |
| Processing | `/wallet/payout/{ref}/process` | `/wallet/payout/{ref}/process` |
| Execution | PayOS (`POST /v1/payouts`) | External bank transfer |
| Completion | PayOS webhook/polling or manual override | `/wallet/payout/{ref}/complete` |
| Failure | Webhook/polling/PayOS error → `/fail` | `/wallet/payout/{ref}/fail` |
| Reconciliation | `/api/v1/payos/...` endpoints | Evidence files + notes |

---

### 7. Operational Tips & Gotchas
- **Date filters:** PayOS requires ISO 8601 without fractional seconds (e.g., `2025-11-24T08:00:00Z`). We explicitly truncate nanos before calling PayOS.
- **Category field:** Keep as array in JSON body, but join with commas when generating signatures.
- **Idempotency:** Always reuse the SSOT `idempotencyKey` when retrying `createPayoutOrder` to avoid double disbursement.
- **Logging:** The raw PayOS response isn’t stored on transactions to prevent oversized notes. Rely on logs (`PayOSPayoutClient`) when deeper debugging is needed.
- **Manual evidence:** Do not mark `SUCCESS` without attaching proof; it’s the only verification for auditors.
- **Balance reconciliation:** Compare PayOS account balance endpoint with internal ledger daily to detect drift early.

