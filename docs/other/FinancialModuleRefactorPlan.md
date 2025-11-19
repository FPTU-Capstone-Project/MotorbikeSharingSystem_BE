# Financial Module Refactor Plan

This document consolidates the proposed backend and frontend refactors for the wallet/transaction subsystem so the platform can provide auditable financial data, richer analytics, and actionable tools for administrators.

---

## 1. Backend Initiatives

### 1.1 Reporting & Analytics Service
- **Implement `ReportService` + persistence layer** to back the existing `ReportController` endpoints (`/reports/wallet/dashboard`, `/topup-trends`, `/commission`). Aggregate directly from the `transactions` table to provide:
  - Total active wallets, wallet balances, and pending liabilities.
  - Top-up / payout / capture trends per day/week/month.
  - Commission totals, balances per driver/ride, and forecast of pending commissions.
- **Expose wallet bucket balances** (SYSTEM.MASTER, SYSTEM.COMMISSION, SYSTEM.PROMO) and outstanding user liabilities so admins can validate solvency at any time.

### 1.2 Transaction Query Enhancements
- Extend `/api/v1/transaction/all` to honor filters for `type`, `status`, `direction`, `actorKind`, `systemWallet`, date range, `sharedRideId`, and `groupId`.
- Add supporting repository methods (specifications or custom queries) to keep pagination performant.
- Provide auxiliary endpoints:
  - `/transaction/group/{groupId}` – returns the full double-entry set so the UI can narrate each financial story.
  - `/transaction/ride/{sharedRideId}` – reveals every hold/capture/payout linked to a ride.
  - `/transaction/system` – lists only SYSTEM wallet rows for audits.

### 1.3 Operational Actions & Controls
- **Payout approvals**: introduce endpoints (e.g., `/admin/payouts/{id}/approve|reject`) that transition `TransactionType.PAYOUT` rows, mirror SYSTEM.MASTER movements, and trigger PSP integrations.
- **Wallet reconciliation**: expose `/admin/wallets/{userId}/reconcile` to invoke `WalletService.reconcileWalletBalance`.
- **Anomaly detection**: scheduled jobs or `/reports/wallet/anomalies` endpoint that flags unbalanced transaction groups, stale PENDING rows, or missing SYSTEM.COMMISSION mirrors. Store audit logs for each resolution.

### 1.4 DTO & Batch Utilities
- Enrich `TransactionResponse` with structured metadata (counterparty info, PSP reference, ride/request identifiers).
- Add a batch user lookup endpoint for the admin UI (`/admin/users/bulk?ids=...`) to avoid N+1 profile calls when rendering large tables.

### 1.5 Testing & Documentation
- Add integration tests for each reporting endpoint and critical transaction filters.
- Document the double-entry expectations (e.g., TOPUP mirror rows, CAPTURE trio) in the backend README so future maintainers understand the ledger constraints.

---

## 2. Frontend Initiatives (Admin Portal – “Thanh toán”)

### 2.1 Data Contract Alignment
- Update `transactionService` typings so they match the backend DTO (remove legacy `riderUserId/driverUserId` fields, add `systemWallet`, `sharedRideId`, etc.).
- Send only the filters supported by the backend and reflect server-side pagination metadata accurately.

### 2.2 Wallet Health Dashboard
- Replace the current hard-coded stat cards with real metrics returned by `/reports/wallet/dashboard` and `/reports/wallet/commission`.
- Surface balances for Master, Commission, Promo wallets, pending liabilities, and counts of active wallets/pending payouts.
- Include alert badges when liabilities exceed available Master cash or when anomaly endpoints report issues.

### 2.3 Cashflow & Trend Visualizations
- Use `/reports/wallet/topup-trends` to render stacked charts of top-ups, holds, captures, refunds, and payouts per interval.
- Add a commission share visualization showing rider fare vs. driver payout vs. platform commission for a selected date range.

### 2.4 Transaction Journal & Group Explorer
- Group table rows by `groupId` so each entry displays the rider/driver/system legs together, highlighting directions, wallet snapshots, and PSP references.
- Provide quick filters (e.g., “Show only commission ledger entries” or “Show SYSTEM.MASTER mirror rows”) to help finance focus on specific funds.
- Enhance the details modal with ride/request references, double-entry visualization, and imbalance warnings.

### 2.5 Actions & Work Queues
- Introduce actionable cards/tables fed by new backend endpoints, such as:
  - “Payout approvals” queue with approve/reject buttons tied to the real APIs.
  - “Reconciliation required” list for wallets flagged by anomaly detection.
  - “Pending PSP confirmations” list for TOPUPs or PAYOUTs waiting on external references.

### 2.6 User Context & Performance
- Swap the per-transaction profile fetches with the new backend batch endpoint.
- Cache profile responses and transaction filters client-side to avoid reloading when admins revisit the page.

---

## 3. Admin Visibility & Actions

### 3.1 What Admins Can See
- **Real-time wallet health**: Master/Commission/Promo balances, pending liabilities, and variance indicators.
- **Cashflow trends**: Daily/weekly/monthly charts for top-ups, payouts, holds, and captures, plus commission share breakdowns.
- **Transaction narratives**: For every `groupId`, admins see the complete double-entry set (rider OUT, driver IN, SYSTEM.COMMISSION IN, PSP mirrors) with timestamps, PSP refs, and balance snapshots.
- **Anomaly reports**: Highlighted exceptions such as stale pending transactions, unbalanced groups, or missing mirror entries.

### 3.2 Actions & Financial Operations
- Approve/reject driver payout requests (updates both user wallet and SYSTEM.MASTER mirror).
- Trigger wallet reconciliations for specific users or in bulk when anomalies appear.
- Drill into a ride’s financial history (holds, releases, captures, refunds) to resolve disputes.
- Export commission reports per driver/period for accounting handoffs.
- Initiate adjustments or promo credits via dedicated endpoints, with audit logging and notifications.

---

## 4. Implementation Notes
- Prioritize backend reporting endpoints so frontend cards/charts can consume authoritative data.
- Roll out backend filter changes before shipping the new transaction table UI to avoid broken queries.
- Coordinate API versioning or feature flags to keep the current admin UI working while the refactor is in progress.
- Add observability (metrics/logging) around new services so anomalies and long-running aggregations are traceable.

This plan ensures the financial module adheres to double-entry principles, exposes clear monitoring dashboards, and equips administrators with the tools they need to act on wallet and transaction events confidently.
