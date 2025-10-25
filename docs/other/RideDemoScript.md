# Ride Lifecycle Demo & QA Script

**Audience**: QA engineers, demo facilitators, backend engineers  
**Purpose**: Provide an end-to-end walkthrough of core ride-sharing flows (driver-initiated ride, rider join, AI booking, driver-claimed request) across Rider, Driver, and Admin actors.  
**Prerequisites**: Backend services running with WebSocket broker, pricing service stub/real available, database seeded with reference locations/vehicles.  
**Last Updated**: October 24, 2025

---

## 1. Shared Environment Setup

| Item         | Requirement                                                                                                                                                     |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Backend      | Deploy branch `ride-lifecycle` (or newer) with migrations up to `V10__Fix_notification_table_constraint.sql`.                                                   |
| Config       | `app.ride.*` properties retain defaults (broadcast enabled, autoLifecycle enabled). `app.timezone` set to `Asia/Ho_Chi_Minh`.                                   |
| Notification | WebSocket endpoint `wss://<host>/ws` reachable with STOMP destinations `/user/queue/ride-offers`, `/user/queue/ride-matching`, `/user/queue/notifications`.     |
| Pricing      | Quote service available at `POST /api/v1/quotes` (faker/stub acceptable).                                                                                       |
| Locations    | Database contains at least: <br> `11` - Vinhomes Grand Park S2.02 (10.8386317, 106.8318038) <br> `12` - FPT University Main Gate (10.8414800, 106.8098440) <br> |
| Tools        | REST client (Insomnia/Postman), WebSocket client (webstompdev or browser console), DB console.                                                                  |

---

## 2. Actor Records & Test Data

Create/seed these records via SQL or admin APIs before the demo.

### 2.1 Rider
- **User**  
  - Email: `john.doe@example.com`  
  - Password: `Password1!`  
  - Full name: `John Doe`  
  - Role: `RIDER`  
  - Status: `ACTIVE` (email + phone verified)  
- **Rider Profile**  
  - Rider ID: `2001` (assumed)  
  - Status: `ACTIVE`  
  - Preferred payment: `WALLET`  
  - Wallet balance >= `300000` VND (pending + shadow).  
- **WebSocket**: subscribe to `/user/<userId>/queue/ride-matching`.

### 2.2 Driver
- **User**  
  - Email: `driver1@example.com`  
  - Password: `Password1!`  
  - Full name: `Driver One`  
  - Role: `DRIVER`  
  - Status: `ACTIVE`  
- **Driver Profile**  
  - Driver ID: `3001`  
  - Status: `ACTIVE`  
  - Max passengers: `1`  
  - Max detour minutes: `8`  
  - Rating average: `4.9`  
- **Vehicle**  
  - Vehicle ID: `8`  
  - Plate: `29A-12344`  
  - Capacity: `1`  
  - Status: `APPROVED`  
  - Linked to driver ID `15`.  
- **Wallet**: pending funds `0`, shadow `300000` VND (for fare capture).  
- **WebSocket**: subscribe to `/user/<userId>/queue/ride-offers`.

[//]: # (### 2.3 Admin &#40;Admin Ops&#41;)

[//]: # (- **User**  )

[//]: # (  - Email: `admin.ops@mssus.edu`  )

[//]: # (  - Password: `RideDemo!123`  )

[//]: # (  - Role: `ADMIN` &#40;`ROLE_ADMIN`&#41;  )

[//]: # (  - Status: `ACTIVE`  )

[//]: # (- **Responsibilities**: monitor lifecycle changes, inspect fares, optionally insert ratings.)

### 2.3 Supporting Data
- Quotes generated on demand during each flow.  
- Ensure scheduler components (`RideMatchingCoordinator`, `RideLifecycleWorker`) are running.

---

## 3. Flow Index

1. Driver initiates a shared ride (driver-led lifecycle).  
2. Rider joins an existing shared ride (join request).  
3. Rider books an AI-matched ride (system-led matching).  
4. Driver claims a rider’s published request (broadcast marketplace).

Each flow can be executed independently; reset data between flows if desired.

---

## 4. Flow Scripts

### Flow 1 - Driver Initiates a Shared Ride

**Goal**: Driver schedules a shared ride, rider joins it, ride proceeds to completion, fare captured.

#### Preconditions
- Driver and rider JWT tokens available.  
- Locations `11` and `12` valid.  
- Vehicle `8` approved for driver.  
- Rider wallet covers expected fare (~20,000 VND).

#### Steps
1. **Driver creates shared ride**  
   `POST /api/v1/rides` (Driver JWT)  
   ```json
   {
     "startLocationId": 11,
     "endLocationId": 12
   }
   ```  
   Response contains `sharedRideId` (example: `9001`) and status `ONGOING`, as well as tracking for ride started.

2. **Tracking**  
   `POST /api/v1/rides/rides/{rideId}/track` (Driver JWT)
    Example request body:
   ```json
   [
     {
       "lat": 0.1,
       "lng": 0.1,
       "timestamp": "2025-10-23T14:14:05.354Z"
     }
   ]
   ```  
   Response contains `currentDistanceKm` (example: `2.2`) and status `OK`.

3. **Rider requests quote**  
   `POST /api/v1/quotes` (Rider JWT)  
   ```json
   {
     "pickup": { "latitude": 10.84290, "longitude": 106.81954 },
     "dropoffLocationId": 12
   }
   ```  
   Store `quoteId` from response.

4. **Rider submits join request**  
   `POST /api/v1/ride-requests/rides/{rideId}` (Rider JWT)  
   ```json
   {
     "quoteId": "<UUID from step 2>"
   }
   ```  
   Response returns `sharedRideRequestId` (example: `9101`) with status `PENDING`. Wallet hold is placed automatically.

5. **Driver reviews request**  
   - REST: `GET /api/v1/ride-requests/rides/{rideId}` (Driver JWT) → list includes request `{requestId}`.  
   - WebSocket: confirm payload appears on `/user/<driverId>/queue/ride-offers`.

6. **Driver accepts**  
   `POST /api/v1/ride-requests/{requestId}/accept` (Driver JWT)  
   ```json
   { "rideId": "{requestId}" }
   ```  
   Status moves to `CONFIRMED`; passenger count increments.

7. **Driver starts ride request to marks rider picked up**  
   `POST /api/v1/rides/start-ride-request` (Driver JWT)  
   ```json
   {
     "rideId": "{rideId}",
     "rideRequestId": "{requestId}"
   }
   ```  
   Shared ride request status becomes `ONGOING`.

8. **Driver completes ride request (drop-off)**  
   `POST /api/v1/rides/complete-ride-request` (Driver JWT)  
   ```json
   {
     "rideId": "{rideId}",
     "rideRequestId": "{requestId}"
   }
   ```  
   Response contains `RideRequestCompletionResponse` with captured fare and commission numbers.

9. **Driver completes ride**  
   `POST /api/v1/rides/{rideId}/complete` (Driver JWT)  
   ```json
   { "rideId": "{rideId}" }
   ```  
   Status becomes `COMPLETED`; tracking stops automatically.

10. **Verification**  
    - Rider view: `GET /api/v1/ride-requests/{requestId}` (Rider JWT) → status `COMPLETED`.  
    - Ride view: `GET /api/v1/rides/{rideId}` (Driver or Admin JWT) → status `COMPLETED`, passenger count decremented.  
    - Wallet: optional DB check (`ride_transactions` table) to confirm capture.

11. **Optional rating**  
    If the ride-review service is available, submit rider rating via the corresponding endpoint; otherwise log a manual note.

**Post-conditions**: Ride and request are `COMPLETED`; wallet capture recorded; notifications dispatched to rider and driver.

---

### Flow 2 - Rider Joins an Existing Shared Ride

**Goal**: Rider browses available rides, joins one, driver accepts, ride completes, rider leaves feedback.

#### Preconditions
- Optionally reset data from Flow 1.  
- Driver creates a new scheduled ride (use step 1 but with departure `2025-10-25T07:15:00`, store new `sharedRideId` e.g., `9201`).  
- Rider wallet funded.

#### Steps
1. **Rider browses rides**  
   `GET /api/v1/rides/available?startTime=2025-10-25T07:00:00&endTime=2025-10-25T08:00:00&page=0&pageSize=10` (Rider JWT)  
   Response paginates rides; identify ride `9201`.

2. **Rider generates quote**  
   Same as Flow 1 step 2, but adjust `desiredPickupTime` to align with new schedule.

3. **Rider submits join request**  
   `POST /api/v1/ride-requests/rides/9201` (Rider JWT) with new `quoteId`. Response returns request ID `92011`.

4. **Driver reviews and optionally declines**  
   - To test decline path: `POST /api/v1/ride-requests/92011/reject?reason=Seat%20taken` (Driver JWT). Verify rider sees status `CANCELLED`.  
   - For acceptance flow, resubmit join request and proceed.

5. **Driver accepts**  
   `POST /api/v1/ride-requests/{requestId}/accept` (Driver JWT) with `rideId = 9201`.

6. **Lifecycle completion**  
   Repeat steps 6-9 from Flow 1 with IDs `9201` and the new request ID. Ensure statuses progress to `ONGOING`/`COMPLETED`.

7. **Rider feedback**  
   If the rating API is available, submit rating; otherwise annotate manually for demo.

**Post-conditions**: Completed ride/request pair, rider saw available rides filtered by time window, decline scenario optionally demonstrated.

---

### Flow 3 - Rider Books an AI-Matched Ride

**Goal**: Rider books a ride without choosing a driver; system matches and driver accepts.

#### Preconditions
- Driver creates a scheduled ride for matching (e.g., `POST /api/v1/rides` start `11`, end `14`, departure `2025-10-25T07:45:00` → ride `9301`).  
- Worker schedulers active.

#### Steps
1. **Rider obtains quote**  
   `POST /api/v1/quotes` (Rider JWT) with pickup near `11`, drop-off `14`.

2. **Rider submits AI booking**  
   `POST /api/v1/ride-requests` (Rider JWT)  
   ```json
   {
     "quoteId": "<UUID>",
     "desiredPickupTime": "2025-10-25T07:50:00",
     "notes": "Boarding at Gate 3"
   }
   ```  
   Response returns request ID `93011` in status `PENDING`.

3. **Coordinator processes match**  
   - Monitor logs (`RideMatchingCoordinator`) or wait ~5 seconds.  
   - Driver receives sequential offer on `/user/<driverId>/queue/ride-offers`.  
   - Rider sees proposals via `GET /api/v1/ride-requests/{requestId}/matches` (if exposed).

4. **Driver accepts matched rider**  
   `POST /api/v1/ride-requests/93011/accept` (Driver JWT)  
   ```json
   { "rideId": 9301 }
   ```  
   Status becomes `CONFIRMED`.

5. **Broadcast fallback (optional)**  
   If no driver accepts within the window, the request automatically moves to `BROADCASTING`. Observe via `GET /api/v1/ride-requests/{requestId}`. Resume flow after acceptance.

6. **Lifecycle completion**  
   Execute steps 6-9 from Flow 1 for ride `9301` and request `93011`.

7. **Admin verification**  
   Admin queries `GET /api/v1/rides/{rideId}` to ensure `matchScore`, detour information, and completion metrics are recorded.

**Post-conditions**: AI-created request matched to the scheduled ride, acceptance recorded, ride completed end-to-end.

---

### Flow 4 - Driver Claims a Rider’s Published Request

**Goal**: Demonstrate broadcast marketplace where a driver proactively claims a rider request in `BROADCASTING` status.

#### Preconditions
- Ensure there are no scheduled rides that would absorb the request during sequential matching.  
- Rider submits an AI booking as in Flow 3 (new quote, pickup `11`, drop-off `13`, departure immediate).  
- Allow matching coordinator to exhaust candidates (no driver should accept). After ~30 seconds the request should be set to `BROADCASTING`. Confirm via `GET /api/v1/ride-requests/{requestId}`.

#### Steps
1. **Driver queries broadcast marketplace**  
   `GET /api/v1/ride-requests/broadcasting` (Driver JWT)  
   Response returns array of `BroadcastingRideRequestResponse`; note `rideRequestId` (example `94011`).

2. **Driver claims request**  
   `POST /api/v1/ride-requests/94011/broadcast/accept` (Driver JWT)  
   ```json
   { "vehicleId": 4001 }
   ```  
   Response returns `SharedRideRequestResponse` with new `sharedRideId` created by the system and status `CONFIRMED`.

3. **Rider confirms**  
   `GET /api/v1/ride-requests/94011` (Rider JWT) to verify status `CONFIRMED` and new driver details. Rider can acknowledge in UI; no further API call required.

4. **Lifecycle completion**  
   - Driver starts ride: `POST /api/v1/rides/<newRideId>/start` with body containing `rideId`.  
   - Driver marks pickup: `POST /api/v1/rides/start-ride-request`.  
   - Driver completes request: `POST /api/v1/rides/complete-ride-request`.  
   - Driver completes ride: `POST /api/v1/rides/<newRideId>/complete`.

5. **Verification**  
   - `GET /api/v1/ride-requests/<id>` → `COMPLETED`.  
   - `GET /api/v1/rides/<newRideId>` → `COMPLETED`.  
   - Admin can review newly created ride in reporting dashboards.

**Post-conditions**: Broadcast request is served without sequential suggestions, new ride stitched automatically, fare captured, notifications sent.

---

## 5. Optional Variations

1. **Multiple riders per ride**: Repeat join steps to demonstrate capacity limits and detour thresholds.  
2. **Auto-lifecycle enforcement**: Let ride/request idle beyond configured windows; observe worker auto-start/auto-complete behaviour and lifecycle notifications.  
3. **WebSocket only demo**: Instead of polling, rely solely on STOMP messages to verify offer, confirmation, and completion notifications.  
4. **Failure scenarios**: Intentionally reject join requests, cancel rides (`DELETE /api/v1/rides/{id}`), or trigger broadcast timeouts to show resilience.

---

## 6. Cleanup

- Cancel or delete demo rides if necessary (`DELETE /api/v1/rides/{id}?reason=demo_completed`).  
- Refund rider wallet if funds were captured during demo (manual DB or wallet service call).  
- Remove any temporary quotes or broadcast requests if they clutter the system.  
- Disconnect WebSocket clients.

---

**Contact**: ride-lifecycle feature owner (backend-dev@mssus.edu)  
**Revision History**:  
- 2025-10-24 – Initial draft covering four ride flows.
