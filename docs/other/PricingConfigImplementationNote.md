# Pricing Configuration – Implementation Note

_Last updated: April 2025_

This document explains the pricing configuration system (versioned configs + tiered fares) with emphasis on **constraints and operational controls**. It is intended for reviewers and operators who need to understand how pricing changes are created, validated, scheduled, activated, and communicated to users.

---

## 1. Executive Summary

### 1.1 What Was Built
- A **versioned pricing configuration** model with explicit lifecycle states: DRAFT → SCHEDULED → ACTIVE → ARCHIVED.
- A **tiered fare model** (fixed fares per distance band) with guardrails for contiguity, coverage (0–25 km), and non-decreasing amounts.
- **Admin APIs** to create drafts, edit metadata, add/update/delete/replace tiers, schedule go-live, and archive configs.
- **Scheduling rules** that enforce a 24h notice and align go-live to **03:00 Hanoi (UTC+7)**; only one config can be scheduled at a time.
- **Activation scheduler** that promotes scheduled configs to ACTIVE and archives the previous one.
- **User notifications** (in-app SYSTEM) on schedule creation, highlighting go-live time and base fare.

### 1.2 Why This Design
- Pricing changes are risky; constraints prevent gaps/overlaps, surprise fare flips, and silent regressions.
- Versioned lifecycle enables auditing and controlled rollout without mutating live configs.
- Scheduled activation at a quiet hour (03:00 UTC+7) with 24h lead time meets BR-29 (advance notice) and reduces churn.
- Tier guardrails encode BR-41 (fixed tiered policy) and service-area limits (<=25 km).

---

## 2. Data Model & Migrations

### 2.1 Schema changes (`src/main/resources/db/migration/V30__Admin_pricing_config_controls.sql`)
- `pricing_configs` additions:
  - `status` ENUM-like (DRAFT, SCHEDULED, ACTIVE, ARCHIVED)
  - `created_by`, `updated_by` (FK to `users`)
  - `change_reason` (string), `notice_sent_at` (timestamp)
  - `valid_from` allowed null for drafts; `valid_until > valid_from` when present
  - Index: `(status, valid_from)` for lookups
- `fare_tiers` constraint: `max_km <= 25` (service envelope)
- Status backfill: existing configs marked ACTIVE if `valid_until IS NULL` else ARCHIVED.

### 2.2 Entities & mapping
- `PricingConfig` (`src/main/java/com/mssus/app/entity/PricingConfig.java`): now includes `status`, `createdBy`, `updatedBy`, `changeReason`, `noticeSentAt`; `validFrom` nullable.
- `PricingConfigStatus` enum (`common/enums/PricingConfigStatus.java`).
- `PricingConfigDomain` carries `status` for pricing policy layer.
- `PricingConfigMapper` maps status and ignores lifecycle fields on `toEntity` (they are set in service). Mapping intentionally leaves fare tiers to be injected separately.

### 2.3 Seed data
- `R__Seed_data.sql` marks the sample config as ACTIVE with status.

---

## 3. Core Components & Responsibilities

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `PricingConfigAdminService` | `src/main/java/com/mssus/app/service/PricingConfigAdminService.java` | Business logic for lifecycle transitions, tier validation, scheduling, notifications, and persistence |
| `AdminPricingConfigController` | `src/main/java/com/mssus/app/controller/AdminPricingConfigController.java` | Admin REST API surface for listing, detail, CRUD on configs and tiers, scheduling, archiving |
| `PricingConfigActivationScheduler` | `src/main/java/com/mssus/app/scheduler/PricingConfigActivationScheduler.java` | Periodically promotes SCHEDULED → ACTIVE at/after `valid_from`, archives previous ACTIVE |
| `PricingConfigRepository` | `src/main/java/com/mssus/app/repository/PricingConfigRepository.java` | Fetch ACTIVE/SCHEDULED configs, lookup by status, pagination |
| `FareTierRepository` | `src/main/java/com/mssus/app/repository/FareTierRepository.java` | CRUD on tiers tied to a config |
| `PricingServiceImpl` | `src/main/java/com/mssus/app/service/domain/pricing/impl/PricingServiceImpl.java` | Runtime pricing: fetches active config, loads active tiers, computes fare |
| DTOs (request/response) | `src/main/java/com/mssus/app/dto/request/pricing/*`, `.../response/pricing/*` | Payloads for admin APIs and tier manipulation |
| Migration | `src/main/resources/db/migration/V30__Admin_pricing_config_controls.sql` | Schema changes for lifecycle and constraints |

---

## 4. Lifecycle & State Model

### 4.1 States
- **DRAFT**: Editable config. No `valid_from`. Not used for pricing.
- **SCHEDULED**: Frozen config awaiting activation. Has `valid_from` (computed go-live), `valid_until = null`.
- **ACTIVE**: Live config. Enforced by repo query `findActive(now)` (status IN ACTIVE/SCHEDULED with window check to bridge at activation moment).
- **ARCHIVED**: Historical, immutable.

### 4.2 Transitions
- Create → DRAFT (via POST /pricing-configs)
- DRAFT/SCHEDULED → DRAFT/SCHEDULED (metadata or tier edits allowed)
- DRAFT/SCHEDULED → SCHEDULED (POST /{id}/schedule) sets `valid_from`, resets `notice_sent_at`, closes prior ACTIVE’s `valid_until`
+- SCHEDULED → ACTIVE (scheduler when `valid_from <= now`)
- ACTIVE → ARCHIVED (automatic on activation of a new config)
- DRAFT/SCHEDULED → ARCHIVED (manual archive endpoint; blocked if ACTIVE)

---

## 5. Tier Rules & Validation (applied on every mutation)
- Tier levels **sequential starting at 1**.
- First tier **must start at 0 km**.
- **Contiguous**: `next.minKm == prev.maxKm` (no gaps/overlaps).
- **Coverage**: must end at exactly **25 km** (service limit). Max per tier `maxKm <= 25`.
- **Ordering**: `minKm < maxKm` per tier.
- **Monotonic amounts**: each subsequent tier amount >= previous (non-decreasing base fares).
- **Non-empty**: at least one tier required.
- Validation runs on add/update/delete/replace and on scheduling; operations fail fast on violation.

---

## 6. Scheduling & Activation

### 6.1 Go-live computation
- Enforces **≥24h notice** from request time.
- Aligns to **03:00 Hanoi (Asia/Ho_Chi_Minh)** after the 24h threshold.
- If config already has a later `valid_from`, that later time wins.
- Stored as `valid_from` and mirrored as `version` (version tracks go-live instant).

### 6.2 Single scheduled config
- Only one config can be SCHEDULED at any moment. Scheduling a new one fails until the existing scheduled config is cancelled/archived.

### 6.3 Activation scheduler
- `PricingConfigActivationScheduler` runs every 5 minutes (cron `0 */5 * * * *`).
- If a SCHEDULED config’s `valid_from <= now`, it is promoted to ACTIVE; previous ACTIVE (if any) is archived and its `valid_until` is closed to the new `valid_from`.

---

## 7. Admin API Surface

Base path: `/api/v1/admin/pricing-configs` (ADMIN role, bearer auth).

| Endpoint | Purpose | Notes |
|----------|---------|-------|
| `GET /` | List configs (optional `status` filter, pageable) | Uses repo pagination |
| `GET /{id}` | Detail with tiers | Returns current tier set |
| `POST /` | Create DRAFT | Requires commission rate, changeReason, initial tiers |
| `PUT /{id}` | Update metadata (commission, changeReason) | Only DRAFT/SCHEDULED |
| `PUT /{id}/tiers` | Replace all tiers | Only DRAFT/SCHEDULED; revalidates entire set |
| `POST /{id}/tiers` | Add a tier | Revalidates full set after insertion |
| `PUT /{id}/tiers/{tierId}` | Update a tier | Revalidates full set with replacement |
| `DELETE /{id}/tiers/{tierId}` | Delete a tier | Revalidates remaining tiers; must still cover 0–25 km contiguously |
| `POST /{id}/schedule` | Schedule go-live | Enforces 24h + 03:00 UTC+7, single scheduled, tier constraints |
| `POST /{id}/archive` | Archive non-ACTIVE config | Blocks if ACTIVE |

Validation errors are surfaced as domain validation exceptions (HTTP 400), matching existing error handling patterns.

---

## 8. Notifications
- When scheduling, `PricingConfigAdminService` sends a **SYSTEM** in-app notification (via `NotificationService`) to active users.
- Message content: user-friendly go-live date/time (Hanoi local) and base fare (tier 1 amount). Avoids technical jargon.
- `notice_sent_at` prevents duplicate sends.
- If `valid_from` is missing (should not happen for SCHEDULED), notification is skipped with a warning log.

---

## 9. Runtime Pricing Behavior
- `PricingServiceImpl` uses `PricingConfigRepository.findActive(now)` (status in ACTIVE/SCHEDULED with time window) to resolve the current config.
- Loads tiers for that config and filters `is_active = true` (or null treated as active) before computing fares.
- `StandardFarePolicy` expects contiguous tiers and throws explicit errors for missing tiers (`pricing.validation.no-matching-tier`, `pricing.validation.no-tiers-configured`).

---

## 10. Failure Handling & Edge Cases
- **No tiers / gaps / overlaps**: validation prevents scheduling or tier mutations; runtime still guards with domain errors if data is corrupted.
- **Multiple scheduled configs**: rejected at scheduling time.
- **Edits on ACTIVE/ARCHIVED**: blocked; instructs admins to create a new version instead.
- **Notification failures**: not retried here; relies on `NotificationService` internal handling. `notice_sent_at` only set after sending loop completes.
- **Activation drift**: scheduler runs every 5 minutes; windowed `findActive` also includes SCHEDULED configs whose window has opened, so runtime lookups remain stable during transition.

---

## 11. Files of Interest
- Controller: `src/main/java/com/mssus/app/controller/AdminPricingConfigController.java`
- Service: `src/main/java/com/mssus/app/service/PricingConfigAdminService.java`
- Scheduler: `src/main/java/com/mssus/app/scheduler/PricingConfigActivationScheduler.java`
- Repos: `src/main/java/com/mssus/app/repository/PricingConfigRepository.java`, `src/main/java/com/mssus/app/repository/FareTierRepository.java`
- Entities/Domain: `src/main/java/com/mssus/app/entity/PricingConfig.java`, `.../common/enums/PricingConfigStatus.java`, `.../service/domain/pricing/config/PricingConfigDomain.java`
- DTOs: `src/main/java/com/mssus/app/dto/request/pricing/*`, `src/main/java/com/mssus/app/dto/response/pricing/*`
- Pricing runtime: `src/main/java/com/mssus/app/service/domain/pricing/impl/PricingServiceImpl.java`, `.../policy/impl/StandardFarePolicy.java`
- Migration: `src/main/resources/db/migration/V30__Admin_pricing_config_controls.sql`

---

## 12. Follow-Ups / Recommendations
- Add integration tests for tier validation edges (gaps, overlaps, non-monotonic amounts) and scheduling window (24h + 03:00).
- Consider adding a **reschedule/cancel** API for SCHEDULED configs if admins need to shift go-live.
- Add audit logging (actor, before/after diff) for tier mutations and scheduling.
- Confirm notification fan-out performance and consider batching/queuing if user base is large.
- Surface computed go-live time and validation hints clearly in admin UI to reduce support load.
