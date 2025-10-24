# SOS Alert Feature – Implementation Notes

**Version**: 0.9.0 (Foundation)  
**Date**: October 23, 2025  
**Status**: Feature Skeleton Merged  
**Branch**: `sos-flow`

---

## Table of Contents

1. [Implementation Summary](#implementation-summary)  
2. [Data Model & Migrations](#data-model--migrations)  
3. [Code Structure](#code-structure)  
4. [How the SOS Flow Works (Backend View)](#how-the-sos-flow-works-backend-view)  
5. [Alert Lifecycle & State Machine](#alert-lifecycle--state-machine)  
6. [Notification & Escalation Flow](#notification--escalation-flow)  
7. [Configuration & Tuning](#configuration--tuning)  
8. [API Surface](#api-surface)  
9. [Integration Touchpoints](#integration-touchpoints)  
10. [Error Catalog Entries](#error-catalog-entries)  
11. [Deployment & Ops Notes](#deployment--ops-notes)  
12. [Next Steps & Open Questions](#next-steps--open-questions)

---

## 1. Implementation Summary

### Goals
- Allow riders or drivers to trigger a **panic/SOS alert** while on or off a ride.
- Notify configured **emergency contacts**, administrators, and (optionally) campus security.
- Track the escalation timeline so staff can acknowledge/resolution events.
- Keep the feature **extensible** (future auto-call integration, security dashboards).

### Scope Delivered
| Layer | Component | Description |
|-------|-----------|-------------|
| **Controller** | `SosController` | REST surface for alert CRUD, acknowledgement, resolution, and contact management. |
| **Service** | `EmergencyContactService`, `SosAlertService`, `SosAlertEventService` | Core business logic for contacts, alert lifecycle, and timeline entries. |
| **Worker** | `SosEscalationWorker` | Scheduled escalation job enforcing acknowledgement SLA. |
| **Notification** | `SosAlertNotificationListener` | Bridges alert events with existing notification infrastructure (WebSocket + persisted records). |
| **Domain** | `SosAlert`, `SosAlertEvent`, `EmergencyContact` | Entities extended to capture all required metadata. |
| **Config** | `SosConfigurationProperties` | Single source for all SOS tuning knobs. |
| **DTOs** | `TriggerSosRequest`, `SosAlertResponse`, `EmergencyContactRequest`, etc. | API transport objects. |
| **Error Handling** | Error catalog entries under `sos.*` | Keeps responses consistent with platform behaviour. |

---

## 2. Data Model & Migrations

### Database Changes (`V15__sos_feature.sql`)
- `sos_alerts` table:
  - `share_ride_id` is now nullable (SOS may be triggered outside a ride).
  - Added escalation fields: `last_escalated_at`, `next_escalation_at`, `escalation_count`, `ack_deadline`, and boolean markers (`fallback_contact_used`, `auto_call_triggered`, `campus_security_notified`).
  - Added `resolved_by` FK to `users`.
  - Refreshed status constraint (new `ESCALATED` state) and set defaults.
  - Index on `next_escalation_at` for efficient worker scans.
- New table `sos_alert_events`:
  - Captures timeline entries with `event_type`, free-form description, metadata blob, timestamp.
- `emergency_contacts`:
  - Adds auditing columns (`updated_at`), enforces `is_primary NOT NULL DEFAULT false`, ensures legacy rows are normalized.

### Entity Adjustments
- `SosAlert` (entity):
  - Reworked relationships to `User triggeredBy/acknowledgedBy/resolvedBy` (no more int IDs).
  - Added ride snapshot blob, location coordinates, escalation metadata.
- `EmergencyContact`:
  - Switched to builder pattern, auditing, default `isPrimary=false`.
- New `SosAlertEvent` entity for timeline entries.
- Enum updates:
  - `SosAlertStatus`: `ACTIVE → ESCALATED → ACKNOWLEDGED/RESOLVED/FALSE_ALARM`.
  - `SosAlertEventType`: covers CREATED, CONTACT_NOTIFIED, ADMIN_NOTIFIED, ESCALATED, RESOLVED, etc.
  - `NotificationType`: added `SOS_ALERT`, `SOS_ESCALATED`, `SOS_RESOLVED`.

---

## 3. Code Structure

```
com.mssus.app
├── config
│   └── properties
│       └── SosConfigurationProperties
├── controller
│   └── SosController
├── dto
│   └── sos
│       ├── TriggerSosRequest
│       ├── SosAlertResponse
│       ├── EmergencyContactRequest/Response
│       ├── AcknowledgeSosRequest
│       └── ResolveSosRequest
├── entity
│   ├── EmergencyContact (updated)
│   ├── SosAlert (updated)
│   └── SosAlertEvent (new)
├── repository
│   ├── EmergencyContactRepository
│   ├── SosAlertRepository
│   └── SosAlertEventRepository
├── service
│   ├── EmergencyContactService (+impl)
│   ├── SosAlertService (+impl)
│   ├── SosAlertEventService
│   └── event
│       ├── SosAlertTriggeredEvent
│       ├── SosAlertEscalatedEvent
│       └── SosAlertResolvedEvent
├── service.impl
│   ├── EmergencyContactServiceImpl
│   ├── SosAlertServiceImpl
│   ├── SosAlertNotificationListener
│   └── OtpServiceImpl (fallback contact seeding)
└── worker
    └── SosEscalationWorker
```

---

## 4. How the SOS Flow Works (Backend View)

This mirrors the ride matching flow notes and breaks the SOS pipeline into backend stages.

1. **User triggers SOS (POST /api/v1/sos/alerts)**  
- Rider or driver submits latitude/longitude, optional sharedRideId, and description.
- SosController.triggerSos authenticates via Spring Security and hands off to SosAlertService.triggerSos.
- EmergencyContactRepository loads contacts; when none exist and orceFallbackCall is false, the fallback number is recorded automatically.

2. **Alert persisted & timeline seeded**  
- SosAlert entity is saved with status ACTIVE, cknowledgementDeadline = now + acknowledgementTimeout, and serialized contact snapshot.
- SosAlertEventService records CREATED, DISPATCH_REQUESTED, and FALLBACK_CONTACT_USED (if applicable) in sos_alert_events.

3. **Notifications dispatched**  
- SosAlertTriggeredEvent is published to Spring's event bus.
- SosAlertNotificationListener.handleSosAlertTriggered delivers WebSocket + persisted notifications:
  - Origin user (NotificationType.SOS_ALERT).
  - Each configured admin user ID (pp.sos.admin-user-ids).
- Contact notification attempts are logged in the timeline for auditing.

4. **Escalation monitoring (SosEscalationWorker)**  
- Runs every pp.sos.escalation-scan-ms (default 15 seconds).
- Fetches candidates via SosAlertRepository.findEscalationCandidates, locks them with indByIdForUpdate, and transitions to ESCALATED.
- Increments escalationCount, recalculates 
extEscalationAt, and optionally marks campusSecurityNotified.
- Emits SosAlertEscalatedEvent, which notifies admins (NotificationType.SOS_ESCALATED) and logs a timeline entry.

5. **Admin acknowledgement**  
- Admin calls POST /api/v1/sos/alerts/{id}/acknowledge.
- Service assigns cknowledgedBy, stamps cknowledgedAt, clears escalation timers, and appends optional note (NOTE_ADDED).
- Worker no longer escalates once the alert is acknowledged.

6. **Resolution / false alarm**  
- Admin finalizes with POST /api/v1/sos/alerts/{id}/resolve.
- Alert moves to RESOLVED or FALSE_ALARM based on payload flag, storing esolvedBy/esolvedAt and resetting timers.
- SosAlertResolvedEvent pushes NotificationType.SOS_RESOLVED to origin user and admins; timeline records RESOLVED.

7. **Contact management side flow**  
- Users manage contacts through /api/v1/sos/contacts.
- EmergencyContactService enforces a single primary contact; operations generate audit entries via service logs, not the timeline.

8. **Read APIs & reporting**  
- Riders/drivers list personal alerts via /api/v1/sos/alerts/me, optionally filtering by status.
- Admin dashboards consume /api/v1/sos/alerts and /api/v1/sos/alerts/{id}/timeline for operational oversight.
- SosAlertResponse includes snapshot data so UIs can display route context while staff take action.

---
## 5. Alert Lifecycle & State Machine

### State Machine
```
ACTIVE
  | ack deadline reached
  v
ESCALATED
  | admin acknowledges
  v
ACKNOWLEDGED
  | admin resolves (+ falseAlarm flag controls terminal state)
  v
RESOLVED   or   FALSE_ALARM
```

**Transitions**
- `ACTIVE → ESCALATED`: `SosEscalationWorker` when `next_escalation_at <= now`.
- `ESCALATED → ACKNOWLEDGED`: staff acknowledgement endpoint.
- `ACKNOWLEDGED → RESOLVED/FALSE_ALARM`: staff resolution endpoint.
- Terminal states stop escalation timers; optional reopen path can be added later.

### Timeline Events
Each significant action records a `SosAlertEventType` row:
1. `CREATED` – alert persisted.
2. `ORIGINATOR_NOTIFIED` – rider/driver receives confirmation.
3. `CONTACT_NOTIFIED` – per contact entry (primary + fallback).
4. `ADMIN_NOTIFIED` / `CAMPUS_SECURITY_NOTIFIED`.
5. `ESCALATED` – each escalation wave increments the counter.
6. `ACKNOWLEDGED` – staff acknowledgement (optional note).
7. `RESOLVED` – final outcome with resolution notes.

This provides an append-only audit trail for admin UIs.

---

## 6. Notification & Escalation Flow

### Trigger Path
1. `SosAlertService.triggerSos`
   - Validates user, optional ride context, location snapshot.
   - Serializes existing contacts + fallback, sets initial `acknowledgementDeadline` (`now + config.ackTimeout`).
   - Publishes `SosAlertTriggeredEvent`.
2. `SosAlertNotificationListener.handleSosAlertTriggered`
   - Delivers push + WebSocket messages:
     - Origin user → `NotificationType.SOS_ALERT`.
     - Admin distribution list → `NotificationType.SOS_ALERT`.
   - Records event entries for every contact notified.

### Escalation Worker
```
Cron (15s) → SosEscalationWorker.evaluateEscalations
  ├─ Query active alerts with nextEscalationAt <= now
  ├─ Acquire PESSIMISTIC_WRITE lock
  ├─ Update status to ESCALATED, increment counter, compute next escalation
  ├─ Mark campusSecurityNotified (one-time) if configured
  ├─ Publish SosAlertEscalatedEvent
  └─ Persist timeline entry (ESCALATED)
```
Escalation event notifies:
- Admins (`NotificationType.SOS_ESCALATED`)
- Campus security contact list (timeline only, SMS integration TBD)

### Resolution Path
```
Admin POST /sos/alerts/{id}/acknowledge
  → sets acknowledgedBy, clears escalation timers, timeline NOTE (optional)
Admin POST /sos/alerts/{id}/resolve
  → sets resolvedBy, resolvedAt, status → RESOLVED|FALSE_ALARM
  → publishes SosAlertResolvedEvent (originator + admins notified)
```

---

## 7. Configuration & Tuning

`application.properties` defaults (overrides live in `app.sos.*`):

| Property | Default | Purpose |
|----------|---------|---------|
| `app.sos.fallback-emergency-number` | `113` | Used when user has no contacts or force fallback. |
| `app.sos.acknowledgement-timeout` | `PT120S` | SLA before the first escalation wave. |
| `app.sos.escalation-interval` | `PT30S` | Interval between repeated escalation waves. |
| `app.sos.auto-call-enabled` | `true` | Reserved flag for future telephony integration. |
| `app.sos.campus-security-phones` | _empty list_ | Optional broadcast list for campus security. |
| `app.sos.admin-user-ids` | _empty list_ | User IDs that always get notified for SOS alerts. |
| `app.sos.trigger-hold-duration` | `PT5S` | Client UX hint (long press before showing confirmation). |
| `app.sos.escalation-scan-ms` | `15000` | Worker cron frequency (configurable via `@Scheduled`). |

Properties are bound via `SosConfigurationProperties` and registered alongside `RideConfigurationProperties` in `MssusAccountServiceApplication`.

---

## 8. API Surface

`SosController` (all under `/api/v1/sos`):

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/alerts` | Authenticated user | Trigger SOS (rider/driver). |
| `GET` | `/alerts/me` | Authenticated user | List user-owned alerts (filterable by status). |
| `GET` | `/alerts/{id}` | Authenticated user or admin | Fetch alert details (owner or admin). |
| `GET` | `/alerts/{id}/timeline` | Authenticated user or admin | Timeline events. |
| `GET` | `/alerts` | Admin only | List active alerts for backoffice. |
| `POST` | `/alerts/{id}/acknowledge` | Admin | Transition to `ACKNOWLEDGED`. |
| `POST` | `/alerts/{id}/resolve` | Admin | Resolve + set optional false-alarm flag. |
| `GET` | `/contacts` | Authenticated user | List emergency contacts. |
| `POST` | `/contacts` | Authenticated user | Create contact (auto primary when first). |
| `PUT` | `/contacts/{id}` | Authenticated user | Update details + primary flag. |
| `DELETE` | `/contacts/{id}` | Authenticated user | Delete contact (promotes next primary). |
| `POST` | `/contacts/{id}/primary` | Authenticated user | Set contact as primary. |

Security configuration adds `/api/v1/sos/**` to authenticated paths without relying on disabled role guards; admin-specific endpoints still enforce `hasRole('ADMIN')`.

---

## 9. Integration Touchpoints

- **ProfileServiceImpl**  
  - Injects `EmergencyContactService` to surface contact list in `UserProfileResponse.emergencyContacts`.

- **User/DTO updates**  
  - `UserProfileResponse` + `UserResponse` expose contact arrays (legacy `emergency_contact` string removed).

- **OtpServiceImpl**  
  - After email/phone verification completes, seeds fallback emergency contact if user has none:
    ```java
    EmergencyContactRequest.builder()
        .name("Emergency Hotline")
        .phone(sosConfig.getFallbackEmergencyNumber())
        .primary(true)
    ```

- **NotificationSystem**  
  - Reuses `NotificationService.sendNotification` to persist events and push over WebSocket/FCM fallback.
  - New notification types ensure UI can filter SOS vs ride updates.

- **SecurityConfig**  
  - Added `/api/v1/sos/**` to authenticated routes.

---

## 10. Error Catalog Entries

New IDs registered in `errors.yaml` under `domain: sos`:

| ID | HTTP | Usage |
|----|------|-------|
| `sos.contact.invalid-name` | 400 | Name missing when creating/updating contact. |
| `sos.contact.invalid-phone` | 400 | Phone missing/invalid format. |
| `sos.contact.not-found` | 404 | Contact does not belong to the caller. |
| `sos.alert.invalid-user` | 400 | Trigger attempt without authenticated user. |
| `sos.alert.invalid-request` | 400 | Missing payload data. |
| `sos.alert.ride-not-found` | 404 | Provided rideId not found. |
| `sos.alert.not-found` | 404 | Alert missing. |
| `sos.alert.already-resolved` | 409 | State guard on double resolve. |
| `sos.alert.access-denied` | 403 | Owner/admin guard failure. |
| `sos.alert.contact-serialization-error` | 500 | JSON serialization failure for contact info. |
| `sos.alert.notification-payload-error` | 500 | Notification payload serialization failure. |

These messages surface through `BaseDomainException` to keep API responses consistent.

---

## 11. Deployment & Ops Notes

1. **Migrations**  
   - Apply `V15__sos_feature.sql` to production; watch for locks on `sos_alerts` during ALTER operations.

2. **Configuration**  
   - Set `app.sos.admin-user-ids` to on-call admin user IDs.  
   - If campus security integration is required, populate `app.sos.campus-security-phones` with E.164 numbers.

3. **Notifications**  
   - Ensure WebSocket subscriptions include `/user/queue/sos` on the client side (mirrors ride queues).  
   - Mobile team needs to map new notification types to UI banners (activate panic center screen).

4. **Monitoring**  
   - Add log-based alerts for `"SOS alert escalated"` and `"Fallback emergency contact seeded"` messages.  
   - Future work: metrics counters (e.g., `sos.alert.active`, `sos.alert.escalated`, `sos.alert.resolution.time`).

5. **Permissions**  
   - Admin-only endpoints rely on `ROLE_ADMIN`. Ensure user bootstrap data contains at least one admin for acknowledgement/resolution.

---

## 12. Next Steps & Open Questions

### Immediate (Post-Merge)
- Build staff dashboard to visualize active alerts, timeline entries, and map overlays.
- Hook real telephony/SMS providers for primary contact auto-call + campus security SMS.
- Extend tests (unit + integration) covering escalation worker + controller security.

### Future Enhancements
- Geo-fencing: attach last-known rider location via ride tracking or device telemetry.
- SLA analytics: measure acknowledgement/resolution times, add alerting for breached SLAs.
- Reopen flow: allow admin to reopen a resolved SOS if new info arrives.
- Incident tickets: link SOS alerts to investigation workflow (integration with backoffice tooling).

### Open Questions
- Should we escalate to campus security on first breach or after `n` waves? (Currently first wave flips the flag once.)
- Do we need rate limiting on trigger endpoint to prevent spam? (Currently not implemented.)
- How will frontends prompt users to maintain at least one emergency contact prior to ride booking? (UI/UX task.)

---

**Document Owner**: Backend Team  
**Last Reviewed**: October 23, 2025  
**Status**: Living document – update when behaviour or configuration changes.

