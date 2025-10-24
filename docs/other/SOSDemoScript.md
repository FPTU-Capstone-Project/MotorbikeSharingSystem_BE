# SOS Alert Feature – Demo & QA Script

**Audience**: QA engineers, demo facilitators, backend developers  
**Purpose**: Provide an end-to-end walkthrough of SOS alert behaviour involving Rider, Driver, and Admin actors.  
**Prerequisites**: Backend services running, WebSocket broker active, emails/SMS stubbed or real per environment.  
**Last Updated**: October 23, 2025

---

## 1. Shared Environment Setup

| Item         | Requirement                                                                                                                                         |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| Backend      | Deployed branch `sos-flow` (or higher) with migrations `V15__sos_feature.sql` applied.                                                              |
| Config       | `app.sos.admin-user-ids` includes the admin user below. `app.sos.fallback-emergency-number` left at `113` unless otherwise specified.               |
| Notification | WebSocket endpoints reachable: `wss://<host>/ws` with STOMP destinations `/user/queue/sos`, `/user/queue/ride-offers`, `/user/queue/ride-matching`. |
| Database     | Test data either seeded via SQL or created through APIs as described in section 2.                                                                  |
| Tools        | REST client (Insomnia/Postman), WebSocket client (webstomp, frontend console), database console.                                                    |

---

## 2. Actor Records & Test Data

### 2.1 Rider Account (`Rider User`)
- **User**  
  - Email: `rider.demo@mssus.edu`  
  - Password: `SosRider!23`  
  - Full name: `Rider Demo`  
  - Student ID: `SE2025001`  
  - User Type: `USER`  
  - Status: `ACTIVE`  
  - Email Verified: `true`  
  - Phone Verified: `true`
- **Rider Profile**  
  - Status: `ACTIVE`  
  - Total Rides: `5`  
  - Total Spent: `250000.00` (VND)  
  - Preferred Payment Method: `WALLET`  
  - Emergency Contacts:  
    1. Primary — Name: `Mai Anh`, Phone: `+84901111222`, Relationship: `Sister`  
    2. Secondary — Name: `Tien Nguyen`, Phone: `+84903334455`, Relationship: `Friend`
- **Wallet**  
  - Pending Balance: `0`  
  - Shadow Balance: `500000.00`
- **WebSocket Subscription**  
  - Connect to `/user/<userId>/queue/sos` to receive alert updates.

### 2.2 Driver Account (`Driver User`)
- **User**  
  - Email: `driver.demo@mssus.edu`  
  - Password: `SosDriver!23`  
  - Full name: `Driver Demo`  
  - User Type: `USER`  
  - Status: `ACTIVE`  
  - Email Verified / Phone Verified: `true`
- **Driver Profile**  
  - Status: `ACTIVE`  
  - License Number: `DR123456789`  
  - Vehicle: exists and approved (any valid vehicle record)  
  - Max Passengers: `1`  
  - Rating Avg: `4.8`
- **Rider Profile (optional)**  
  - If dual-role testing is needed; otherwise left null.
- **Emergency Contacts**  
  - Primary — Name: `Trong Le`, Phone: `+84906667788`, Relationship: `Brother`
- **WebSocket Subscription**  
  - Connect to `/user/<userId>/queue/sos` (driver sees own alerts when triggering).  
  - Keep `/user/queue/ride-offers` active for ride events (optional context).

### 2.3 Admin Account (`Admin User`)
- **User**  
  - Email: `admin.safety@mssus.edu`  
  - Password: `SosAdmin!23`  
  - Full name: `Campus Safety Admin`  
  - User Type: `ADMIN`  
  - Status: `ACTIVE`  
  - Email Verified / Phone Verified: `true`
- **Roles & Permissions**  
  - Database record must map to Spring Security role `ROLE_ADMIN`.  
  - Listed under `app.sos.admin-user-ids` property (e.g., user ID `9001`).
- **WebSocket Subscription**  
  - `/user/<userId>/queue/sos` to receive escalation/resolution notifications.

### 2.4 Shared Ride Fixture (Optional but Recommended)
- Shared Ride ID: `5001` (Pre-created)  
- Driver: `Driver User`  
- Status: `ONGOING`  
- Start Location: `FPT University Campus`  
- End Location: `Tech Park`  
- Existing confirmed rider request (optional) for location snapshot context.

---

## 3. Demo Flow Overview

The demo is divided into three phases:
1. **Rider triggers SOS** (with active ride context).  
2. **Escalation behaviour** (simulate acknowledgement/resolution).  
3. **Driver triggers SOS** (off-ride scenario, fallback contact).  

Each phase lists prerequisites, API calls, WebSocket checks, and expected database states.

---

## 4. Detailed Steps

### Phase 1 – Rider Emergency Alert During Ride

#### Preconditions
- Rider is authenticated (JWT token ready).  
- Shared ride `5001` is `ONGOING` with rider as a confirmed passenger (optional but recommended to showcase ride snapshot).  
- Rider has at least one emergency contact configured.

#### Step-by-Step

1. **Trigger SOS Alert**
   - **Endpoint**: `POST /api/v1/sos/alerts`  
   - **Headers**: `Authorization: Bearer <rider_token>`  
   - **Payload**:
     ```json
     {
       "sharedRideId": 5001,
       "currentLat": 10.841231,
       "currentLng": 106.807456,
       "description": "Driver fainted, need immediate assistance.",
       "rideSnapshot": null,
       "forceFallbackCall": false
     }
     ```
   - **Expected Response** `200 OK`:
     - `status`: `ACTIVE`
     - `acknowledgementDeadline`: ~120 seconds from request time
     - `fallbackContactUsed`: `false`
     - `timeline` contains `CREATED` and `DISPATCH_REQUESTED`
   - **Side Effects**:
     - Database: `sos_alerts` row created with `shared_ride_id = 5001`.
     - `sos_alert_events` row with `CREATED`.
   - **Real-time**:
     - Rider WebSocket receives `NotificationType.SOS_ALERT`.
     - Admin WebSocket receives same notification.
     - Emergency contacts will be recorded in timeline as `CONTACT_NOTIFIED` (SMS/Call integration future).

2. **Verify Alert Retrieval (Rider Context)**
   - **Endpoint**: `GET /api/v1/sos/alerts/me`  
   - **Token**: Rider  
   - **Response**: List includes the new alert with status `ACTIVE`.

3. **Admin Lists Active Alerts**
   - **Endpoint**: `GET /api/v1/sos/alerts`  
   - **Token**: Admin  
   - **Expected Result**: Shows alert with Rider name, description preview, status `ACTIVE`.

4. **Admin Reviews Timeline**
   - **Endpoint**: `GET /api/v1/sos/alerts/{alertId}/timeline`  
   - **Token**: Admin  
   - **Verify Entries**:
     - `CREATED`
     - `FALLBACK_CONTACT_USED` absent (since actual contacts exist)
     - `CONTACT_NOTIFIED` entries per contact
     - `ADMIN_NOTIFIED`

#### Notes
- Do **not** acknowledge yet; allow worker to run for Phase 2.

---

### Phase 2 – Escalation and Resolution

#### Preconditions
- Alert from Phase 1 remains `ACTIVE`.  
- Escalation worker running (default 15s interval).
- Admin WebSocket connected.

#### Step-by-Step

1. **Wait for Escalation**
   - Monitor logs: `"SOS alert <id> escalated (count=1)"`.  
   - Timeline should receive `ESCALATED`.  
   - Admin receives `NotificationType.SOS_ESCALATED`.  
   - If `app.sos.campus-security-phones` configured, timeline shows `CAMPUS_SECURITY_NOTIFIED`.

2. **Admin Acknowledges Alert**
   - **Endpoint**: `POST /api/v1/sos/alerts/{alertId}/acknowledge`  
   - **Token**: Admin  
   - **Payload** (optional note):
     ```json
     {
       "note": "Security team dispatched. ETA 5 minutes."
     }
     ```
   - **Response**: `status` now `ACKNOWLEDGED`, `nextEscalationAt` cleared.
   - **Timeline**: `ACKNOWLEDGED` + `NOTE_ADDED`.

3. **Admin Resolves Alert**
   - **Endpoint**: `POST /api/v1/sos/alerts/{alertId}/resolve`  
   - **Token**: Admin  
   - **Payload**:
     ```json
     {
       "resolutionNotes": "Rider received medical assistance. Driver stable.",
       "falseAlarm": false
     }
     ```
   - **Response**: `status` → `RESOLVED`, `resolvedAt` set.
   - **Notifications**:
     - Rider receives `NotificationType.SOS_RESOLVED`.
     - Admin timeline includes `RESOLVED`.

4. **Final Verification**
   - `GET /api/v1/sos/alerts/{id}` (Rider token): status `RESOLVED`, no further escalation scheduled.
   - `GET /api/v1/sos/alerts` (Admin token): resolved alert visible if filters include resolved statuses; otherwise it may be absent from active view.
   - Database check: `escalation_count` equals number of waves, `campus_security_notified` toggled as per configuration.

---

### Phase 3 – Driver SOS Without Emergency Contacts

Purpose: demonstrate fallback behaviour (`113`) when user has no contact entries.

#### Preconditions
- Driver deletes existing emergency contacts:
  - `DELETE /api/v1/sos/contacts/{contactId}` for each entry (Token: Driver).
- Confirm `GET /api/v1/sos/contacts` returns empty list.

#### Step-by-Step

1. **Trigger SOS (No Ride Context)**
   - **Endpoint**: `POST /api/v1/sos/alerts`  
   - **Token**: Driver  
   - **Payload**:
     ```json
     {
       "sharedRideId": null,
       "currentLat": 10.845001,
       "currentLng": 106.812900,
       "alertType": "OTHER",
       "description": "Suspicious person following me near parking lot.",
       "forceFallbackCall": false
     }
     ```
   - **Response**:
     - `fallbackContactUsed`: `true`
     - `contactInfo` JSON includes fallback record with phone `113`
     - `status`: `ACTIVE`
   - **Timeline**: Contains `FALLBACK_CONTACT_USED`.
   - **Notifications**: Admin + driver notified.

2. **Admin Acknowledges & Marks False Alarm**
   - Acknowledge/Resolve sequence similar to Phase 2, but set `falseAlarm: true` on resolution.
   - Verify final status `FALSE_ALARM`.

3. **Post-conditions**
   - Driver contact list remains empty unless newly added.  
   - Timeline includes `RESOLVED` + metadata `"Marked as false alarm"`.

---

## 5. Optional Variations

1. **Force Fallback Even When Contacts Exist**  
   - Set `forceFallbackCall: true` in trigger payload.  
   - Timeline will record both `CONTACT_NOTIFIED` entries and `FALLBACK_CONTACT_USED`.

2. **Campus Security Broadcast**  
   - Populate `app.sos.campus-security-phones` (comma-separated).  
   - On first escalation, timeline logs `CAMPUS_SECURITY_NOTIFIED` with phone numbers in metadata.

3. **Admin Without WebSocket**  
   - Use `GET /api/v1/notifications` to pull persisted entries instead of live WebSocket.

4. **Alert Without Ride Snapshot**  
   - Confirm `rideSnapshot` remains `null`, but `contactInfo` still persists for reporting.

---

## 6. Troubleshooting Checklist

| Symptom | Likely Cause | Resolution |
|---------|--------------|------------|
| Rider/Driver trigger returns `403` | Token missing or wrong user role | Refresh JWT, ensure SecurityContext is set. |
| Alert stays `ACTIVE`, no escalation | Worker disabled or `app.sos.escalation-scan-ms` too high | Ensure scheduler enabled, check logs for worker errors. |
| Admin cannot acknowledge (404) | Alert resolved already or wrong ID | List alerts to confirm ID / status. |
| Fallback contact not used when no contacts exist | `ensureFallbackContact` seeding created contact | Delete fallback contact and retry; confirm `EmergencyContactService` data. |
| No notifications received | WebSocket disconnected or user ID mismatch | Reconnect WS, verify subscription uses numeric user ID as per backend principal. |

---

## 7. Cleanup

- Delete demo alerts if necessary: direct DB delete or allow retention.  
- Restore driver emergency contact if needed.  
- Reset property overrides (`app.sos.*`) if modified for demo.

---

**Contact**: Backend SOS feature owner (backend-dev@mssus.edu)  
**Revision History**:
- 2025-10-23 — Initial draft (Codex automation).  
- _Update this section when flows change._
