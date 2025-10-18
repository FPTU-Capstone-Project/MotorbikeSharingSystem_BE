# Ride Matching Flow – Implementation Notes

_Last updated: October 2025_

This document explains the ride matching work that was implemented in the backend. It covers what was added, how the pieces connect, why we needed concurrency, and how mobile apps should call the APIs. It is written for teammates who have never touched asynchronous code before and for mobile engineers who need a clear request/response sequence.

---

## 1. What Was Added or Changed

- **Coordinator Layer**
  - `RideMatchingCoordinator` – orchestrates the full matching workflow for AI bookings (create request ➜ contact drivers ➜ handle timeouts/acceptance).
  - `DriverDecisionGateway` – tracks which driver currently has an offer and enforces response deadlines.
  - `CandidateSelector` – helper that feeds the coordinator a ranked list of proposals one by one.
  - `MatchingResponseAssembler` – builds payloads for both driver and rider push updates.
- **Notification Payloads**
  - `DriverRideOfferNotification` – JSON structure sent to drivers over WebSocket and stored in the notifications table.
  - `RiderMatchStatusNotification` – JSON structure sent to riders when their request is accepted or expires.
- **Real-Time Notification Service**
  - `RealTimeNotificationService` – bridges the coordinator and the existing notification system (persists records + pushes via STOMP).
- **Configuration**
  - `MatchingConfig` – dedicated thread pool for matching tasks and a scheduler for response timeouts.
  - `RideConfigurationProperties` – new `driverResponseSeconds` property (default 90) that controls the driver’s response window.
- **Shared Ride Request Service Changes**
  - Starts matching as soon as an AI booking is created.
  - Blocks driver acceptance unless the current offer belongs to that driver/ride pair.
  - Notifies the coordinator when a booking is cancelled so matching stops.

No existing flows were removed. JOIN_RIDE and other ride management features continue to behave as before.

---

## 2. How the Matching Flow Works (Backend View)

1. **Rider creates AI booking** (`POST /api/v1/ride-requests`):
   - Request is stored with status `PENDING` and kind `BOOKING`.
   - `RideMatchingCoordinator.initiateMatching` runs asynchronously to populate the offer queue.

2. **Coordinator prepares candidates**:
   - Calls `RideMatchingService.findMatches` to get a ranked list of ride proposals based on time, proximity, detour, etc.
   - Wraps the list in `CandidateSelector`, which returns one candidate at a time.

3. **Offer sent to driver**:
   - The coordinator fetches driver profile + location names.
   - Builds a `DriverRideOfferNotification` payload and sends it through `RealTimeNotificationService`:
     - WebSocket destination: `/user/queue/ride-offers`.
     - Also persists the notification (so drivers can see it later even if offline).
   - `DriverDecisionGateway` starts a timer (90 s default). If the driver doesn’t respond, the offer times out.

4. **Driver actions**:
   - If the driver accepts in time, `SharedRideRequestService.acceptRequest` checks with the gateway to confirm this offer is still active. Only the current driver/ride combo can pass.
   - Wallet hold is placed, passenger count increments, and the rider is marked `CONFIRMED`.
   - On success, the coordinator notifies the rider via `/user/{riderUserId}/queue/ride-matching` and ends the session.
   - If the driver declines, times out, or acceptance fails (wallet issue, seat full, etc.), the coordinator resumes and moves to the next candidate.

5. **No more candidates**:
   - If the list is exhausted or the request times out overall (`requestAcceptTimeout`, default 5 min), the coordinator marks the request `EXPIRED`, releases any active driver offer, and informs the rider that no drivers were available.

6. **Cancellation**:
   - When riders cancel a pending AI booking, `SharedRideRequestService.cancelRequest` asks the coordinator to stop matching and clear timers.

7. **Broadcast fallback** *(new)*:
   - When sequential offers are exhausted but the overall 15 minute matching window still has time remaining, the coordinator switches the request status to `BROADCASTING` and pushes a real-time offer to every eligible active driver who is not engaged in an `ONGOING` ride.
   - Drivers get a 30 second response window (configurable via `app.ride.broadcast.response-window-seconds`). The first driver to accept triggers `SharedRideRequestService.acceptBroadcast`, which creates a new shared ride and confirms the rider.
   - If no driver accepts before the broadcast window closes, the coordinator treats the request as expired, releasing wallet holds and notifying the rider through the existing no-match flow.

---

## 3. Concurrency & Async Concepts (Beginner Friendly)

### 3.1 Why we needed async work
- Drivers must respond while the rider is waiting. Holding the main API thread would block other requests.
- Multiple match attempts can happen at the same time (different riders). Each needs its own timers and background tasks.

### 3.2 Executors and Schedulers
- Think of **executors** as a pool of workers. We added `matchingTaskExecutor` (4–8 threads) just for matching logic. That keeps the load away from email or other async tasks.
- A **scheduled executor** is like an alarm clock. We use `matchingScheduler` to wake up when a driver’s response window expires.

### 3.3 DriverDecisionGateway
- Stores the “active offer” for each request. Only one driver can have the offer at any time.
- When a driver clicks accept, the gateway checks: _“Is this the same driver we’re waiting on? Are they still within the time limit?”_
- If not, the acceptance is rejected immediately (prevents race conditions).

### 3.4 Coordinator State Machine (simplified)
- `MATCHING` → sending offers.
- `AWAITING_CONFIRMATION` → waiting for the current driver to finish acceptance.
- `COMPLETED`, `EXPIRED`, `CANCELLED` → terminal states (no more offers).

### 3.5 Error Handling
- On any exception during acceptance (e.g., wallet hold failure), the coordinator moves to the next driver automatically.
- The rider and driver both receive real-time updates so clients can refresh their UI without polling.

You do not need to touch threads directly. Use the coordinator or gateway APIs; they hide the concurrency details.

---

## 4. API & Client Flow (Mobile Friendly)

### 4.1 Creating a Ride Request (Rider App)
1. Rider obtains a quote (existing flow).
2. Call `POST /api/v1/ride-requests` with the quote ID and desired pickup time.
3. Server responds with `201 Created` and an array of candidate proposals (may be empty). This is a snapshot; matching continues asynchronously.
4. Rider app should subscribe to WebSocket topic `/user/{userId}/queue/ride-matching` to receive:
   - `ACCEPTED` payload when a driver confirms.
   - `NO_MATCH` message if no driver accepts within the timeout.

### 4.2 Driver Offer Flow (Driver App)
1. Driver app subscribes to `/user/{driverId}/queue/ride-offers` once authenticated.
2. When an offer arrives, display the request details and countdown (use `offerExpiresAt`).
3. If driver accepts:
   - Call `POST /api/v1/ride-requests/{requestId}/accept` with body `{ "rideId": <sharedRideId>, "estimatedPickupTime": ... }`.
   - If the offer expired or another driver already accepted, the API returns a validation error. Show a “Offer no longer available” message.
4. On rejection/timeout, the coordinator automatically contacts the next driver. No extra API call required when declining – use the existing reject endpoint if the driver actively declines.

### 4.3 Status Updates
- Riders receive `RiderMatchStatusNotification` via WebSocket and can also fetch `GET /api/v1/ride-requests/{requestId}` for the latest state.
- Drivers can check their notification inbox (existing endpoints) to see missed offers.

---

## 5. Configuration & Tuning

| Property | Location | Default | Description |
|----------|----------|---------|-------------|
| `app.ride.request-accept-timeout` | `application.yml` | 5 minutes | Full matching window before the request expires. |
| `app.ride.matching.driver-response-seconds` | `RideConfigurationProperties` | 90 seconds | How long a driver can hold an offer before it is reassigned. |
| `app.ride.matching.max-proposals` | existing | 10 | Number of candidates returned by matching service. |

To adjust response windows, edit `application.yml` and update `driver-response-seconds`:
```yaml
app:
  ride:
    matching:
      driver-response-seconds: 75
```
No code changes required.

---

## 6. Testing & Verification

- `mvn -DskipTests compile` – smoke build after implementation.
- Manual scenarios to verify:
  - Rider creates booking, receives `NO_MATCH` when no drivers available.
  - Driver accepts first offer, rider receives `ACCEPTED` notification.
  - Driver waits until timeout, next driver receives offer.
  - Rider cancels request while matching is running (timers cleared).

We still need deeper integration tests and load tests for concurrency hotspots (see “Next Steps” below).

---

## 7. Next Steps / TODOs

1. **Client Integration**
   - Ensure rider and driver apps subscribe to the new WebSocket queues.
   - Display countdown timers using `offerExpiresAt`.
2. **Error Messaging**
   - Refine client UX for “offer expired” and “wallet hold failed” responses.
3. **Metrics & Monitoring**
   - Add metrics for time-to-acceptance, timeout counts, and queue depth.
4. **Testing**
   - Add integration tests to simulate multiple drivers responding concurrently.
   - Add automated tests validating that expired sessions don’t leak offers.
5. **Enhancements (Future)**
   - Persist `AiMatchingLog` entries for analytics (already have entity).
   - Replace simple detour heuristic with actual routing service call.
   - Support push re-offers when riders edit pickup time.

---

## 8. Quick Reference

- **New Classes**
  - `com.mssus.app.service.matching.*`
  - `com.mssus.app.dto.notification.*`
  - `com.mssus.app.service.RealTimeNotificationService`
  - `com.mssus.app.config.MatchingConfig`
- **Updated Classes**
  - `SharedRideRequestServiceImpl` (matching orchestration)
  - `RideConfigurationProperties` (driver response window)
- **Key Destinations**
  - Rider WebSocket queue: `/user/{userId}/queue/ride-matching`
  - Driver WebSocket queue: `/user/{driverId}/queue/ride-offers`

Keep this document alongside other ride module references so that anyone onboarding can understand the async pieces without deep-diving into the code first.

---

## Appendix A – Class-by-Class Reference

This appendix breaks down every new class or major change. For each field or method, you will find plain-English explanations and why the code needs it.

### A.1 `MatchingConfig`

| Member | Type | Reason |
|--------|------|--------|
| `matchingTaskExecutor()` | `Executor` (ThreadPoolTaskExecutor) | Provides a pool of worker threads dedicated to matching workflow tasks. Prevents matching from blocking other async features. Core 4 / max 8 threads so multiple requests can be processed concurrently. |
| `matchingScheduler()` | `ScheduledExecutorService` | Alarm clock for response deadlines. When we send an offer to a driver we schedule a timer; when it fires we move to the next driver. Marked daemon so it does not block application shutdown. |

**What is a `ScheduledExecutorService`?**  
Think of it as a reminder system: you hand it a task and a delay (e.g., 90 seconds). It will run the task later on a background thread. We use this to automatically expire driver offers if they do not respond.

### A.2 `CandidateSelector`

| Member | Type | Reason |
|--------|------|--------|
| `proposals` | `List<RideMatchProposalResponse>` | Stores the ranked proposals returned by `RideMatchingService`. Made immutable with `List.copyOf` to avoid accidental changes. |
| `index` | `AtomicInteger` | Thread-safe pointer to the next candidate. Allows coordinator to pull candidates one at a time without extra locking. |
| `next()` | returns `Optional<RideMatchProposalResponse>` | Supplies the next candidate or empty if we ran out. The optional clearly communicates there may be no more drivers. |
| `hasNext()` / `size()` / `asList()` | Utility helpers | Provide read-only access to help with logging, UI hints, and loop decisions. |

### A.3 `DriverDecisionGateway`

| Member | Type | Reason |
|--------|------|--------|
| `matchingScheduler` | `ScheduledExecutorService` (injected) | Used to schedule the timeout task for each driver offer. Every offer gets a timer. |
| `activeOffers` | `Map<Integer, DriverOffer>` (`ConcurrentHashMap`) | Tracks the current offer per request ID. Concurrent map keeps data safe when multiple threads interact (driver acceptance vs. timeout). |
| `DriverOffer` | Inner class with fields `requestId`, `rideId`, `driverId`, `Runnable onTimeout`, `AtomicReference<OfferState> state`, `ScheduledFuture<?> timeoutFuture` | Encapsulates everything we need about an offer. The atomic state prevents race conditions: only one transition (pending → accepting → done) is allowed. `ScheduledFuture` lets us cancel the timeout when driver responds. |

**Key methods**
- `registerOffer(...)` – stores the offer, starts the timeout clock, and ensures any previous offer is cancelled. Prevents multiple drivers from thinking they have the same request.
- `beginAcceptance(...)` – called when a driver taps “accept.” It first checks the map to confirm this driver is the active one. If yes, it cancels the timeout and lets acceptance proceed. If no, it returns false and we send an “offer expired” message.
- `completeAcceptance(...)` / `failAcceptance(...)` / `cancelOffer(...)` – cleanup hooks. They remove the offer and stop the timer. We call them for success, failure, or cancellation respectively.

**Why `ScheduledFuture`?**  
When we schedule a timeout we get back a handle (`ScheduledFuture`). This handle allows us to cancel the timer if the driver responds early. Without cancelling, the timeout would still fire and could incorrectly move to the next driver.

### A.4 `MatchingResponseAssembler`

| Member | Type | Reason |
|--------|------|--------|
| `toDriverOffer(...)` | method | Builds the `DriverRideOfferNotification`. Adds context (location names, rider info, expiry time). The `rank` value is stored so client can show “Offer 1 of 3.” |
| `toRiderMatchSuccess(...)` | method | Converts the accepted proposal into a rider-friendly notification. Includes driver, vehicle, and timing estimates. |
| `toRiderNoMatch(...)` | method | Sends a simple “no drivers available” message. |

This assembler keeps notification formatting in one place so services don’t duplicate it.

### A.5 `DriverRideOfferNotification`

Immutable DTO (`@Value`, `@Builder`) with fields:
- `requestId`, `rideId`, `driverId` – identify the offer.
- `riderId`, `riderName` – context to show the driver.
- `pickup/dropoff` names + coordinates – for maps.
- `pickupTime`, `fareAmount`, `matchScore`, `proposalRank`, `offerExpiresAt` – data to display countdown and price.

Why immutable? Multiple threads may read the notification. Immutable objects are safe to share without locks.

### A.6 `RiderMatchStatusNotification`

Similar immutable DTO for rider updates. Fields include:
- `status` (ACCEPTED, NO_MATCH), `message`,
- `rideId`, `driverId`, `driverName`, `vehicleModel/Plate`,
- `estimatedPickup/Dropoff`, `estimatedFare`.

Clients use these to update UI in real-time.

### A.7 `RealTimeNotificationService`

| Member | Type | Reason |
|--------|------|--------|
| `notificationService` | Existing persistence service | Stores notifications in the DB for audit and offline access. |
| `messagingTemplate` | `SimpMessagingTemplate` | Sends JSON payloads over WebSocket (STOMP). |
| `objectMapper` | `ObjectMapper` | Converts payloads to JSON for persistence. |

**Methods**
- `notifyDriverOffer(...)` – pushes payload to driver WebSocket queue and persists a high-priority notification. Wrapped in `try/catch` to avoid crashing matching flow if push fails.
- `notifyRiderStatus(...)` – same pattern for riders (normal priority). 

**Why two channels?**  
WebSocket gives instant updates but users may be offline. Persisted notifications ensure they can pull missed offers later.

### A.8 `RideMatchingCoordinator`

This is the heart of the matching workflow.

| Member | Type | Reason |
|--------|------|--------|
| `requestRepository`, `driverRepository`, `locationRepository` | Repositories | Fetch persistent data needed during matching (requests, drivers, location names). |
| `rideMatchingService` | Existing service | Supplies ranked ride proposals. |
| `rideConfig` | `RideConfigurationProperties` | Access to timeouts and limits (request accept window, driver response seconds). |
| `notificationService` | `RealTimeNotificationService` | Sends driver and rider updates. |
| `decisionGateway` | `DriverDecisionGateway` | Enforces single-offer-at-a-time rule. |
| `responseAssembler` | `MatchingResponseAssembler` | Builds notification payloads. |
| `matchingExecutor` | `Executor` (injected via `@Qualifier`) | Runs matching tasks asynchronously. Ensures operations (like contacting next driver) happen outside HTTP thread. |
| `sessions` | `ConcurrentHashMap<Integer, MatchingSession>` | Tracks in-progress matching sessions per request ID. Needed so we can stop matching if request cancels or completes. |
| `MatchingSession` | Inner class with state (phase, candidate list, rank counter, expiry time) | Represents a single rider’s matching lifecycle. Synchronization around methods ensures consistent state transitions. |

**Important methods**
- `initiateMatching(requestId)` – entry point. Checks request status, prepares candidate list, stores session, and triggers first offer.
- `scheduleNext(...)` / `offerNextCandidate(...)` – drive the loop. Uses executor so each step runs in background.
- `beginDriverAcceptance(...)` – handshake when driver hits accept. Moves session to awaiting state and verifies via gateway.
- `completeDriverAcceptance(...)` / `failDriverAcceptance(...)` – cleanup after driver response (success or failure). On success we also notify rider.
- `cancelMatching(...)` – stops timers and removes session (called when rider cancels).
- `handleOfferTimeout(...)` – invoked by gateway timer to advance to next driver.
- `handleNoCandidates(...)` – sets request to `EXPIRED` and notifies rider when we run out of options or the overall window expires.

**MatchingSession fields in detail**
- `requestId` – key used in the sessions map and log messages.
- `selector` – `CandidateSelector` instance providing proposals.
- `pickupLocation` / `dropoffLocation` – cached `Location` entities to enrich notifications without re-querying each time.
- `expiresAt` – the overall request deadline (`requestAcceptTimeout`). Prevents matching from running forever.
- `phase` (`AtomicReference<Phase>`) – ensures safe transitions between MATCHING → AWAITING_CONFIRMATION → COMPLETED/EXPIRED/CANCELLED.
- `rankCounter` – increments for each offer sent; used in notifications.
- `currentCandidate` / `currentRank` – remember the last sent proposal so we can validate driver acceptance and notify rider later.

Synchronization strategy: methods that mutate session state (`advance`, `markAwaitingConfirmation`, etc.) are `synchronized` to guarantee only one thread changes that session at a time. Concurrency is limited because each session handles a single rider.

### A.9 `RideConfigurationProperties` (Update)

New field: `driverResponseSeconds`. This is read from `app.ride.matching.driver-response-seconds` and defaults to 90. The coordinator converts it to `Duration` and passes it into the gateway.

### A.10 `SharedRideRequestServiceImpl` (Key Changes)

- Injected `RideMatchingCoordinator`.
- After creating an AI booking, calls `matchingCoordinator.initiateMatching(...)` to start the background process.
- Before a driver can accept a booking request, the service calls `matchingCoordinator.beginDriverAcceptance(...)` to make sure the offer is still valid. If not, it returns a domain error so client can show “Offer expired.”
- On successful acceptance (a booking request, not join ride), it notifies coordinator with `completeDriverAcceptance(...)`.
- If wallet hold fails or any other exception occurs mid-acceptance, it calls `failDriverAcceptance(...)` so coordinator can try the next driver.
- When rider cancels a pending AI booking, it calls `matchingCoordinator.cancelMatching(...)` to stop timers and offers.

### A.11 Concurrency Vocabulary Cheat Sheet

- **Executor** – pool of threads used to run tasks in the background.
- **ScheduledExecutorService** – executor that can run tasks after a delay (used for timeouts).
- **ScheduledFuture** – handle for a scheduled task; lets us cancel it if we finish earlier.
- **ConcurrentHashMap** – thread-safe map that allows concurrent read/write without manual locks.
- **AtomicInteger / AtomicReference** – wrappers around numbers/objects that allow atomic (all-or-nothing) updates without locking.
- **Synchronized block** – ensures only one thread can execute a piece of code at a time (used inside `MatchingSession` to protect mutable fields).

If any of these terms feel unfamiliar, remember: they are just tools to coordinate access to shared data when multiple threads are involved. The coordinator hides most of the complexity; you typically only interact via its public methods.

---

## Appendix B - Broadcast Fallback Technical Notes

This appendix captures the post-MVP broadcast mechanism that kicks in when the ranked matching queue is exhausted. Use it as a checklist during audits or future refactors.

### B.1 Trigger Criteria and Guard Rails

- Broadcast is only attempted for `RequestKind.BOOKING` requests that remain `PENDING` after sequential offers.
- The session must still be within the global `requestAcceptTimeout` window (15 minutes by default). `MatchingSession.remainingMatchingTime()` is consulted before entering broadcast mode.
- The configured window `app.ride.broadcast.response-window-seconds` (default 30) is clamped to the remaining timeout so we never exceed the original SLA.
- Matching stops immediately if the rider cancels or the request moves to a terminal status.

### B.2 Eligibility Query

`DriverProfileRepository.findBroadcastEligibleDrivers(List<Integer> excludedIds)` looks for:

| Predicate | Purpose |
|-----------|---------|
| `status = ACTIVE` | Respect existing driver activation workflow. |
| Driver id not in `excludedIds` | Avoid notifying drivers who already declined/timed out during ranked matching. |
| No `SharedRide` in `ONGOING` | Prevent double booking. |

This query intentionally ignores seat capacity and polygons; those checks remain in the original scoring pipeline. Future work could add geo filters here if needed.

### B.3 Notification Payload Changes

`DriverRideOfferNotification` gained:

| Field | Type | Meaning |
|-------|------|---------|
| `broadcast` | `Boolean` | Distinguishes broadcast alerts from ranked offers (clients show different UI). |
| `responseWindowSeconds` | `Integer` | Countdown the driver should honour. |

`MatchingResponseAssembler.toDriverBroadcastOffer(...)` sets `broadcast = true`, omits `rideId` (new ride is created later), and stamps the shared deadline.

### B.4 Coordinator Flow

Broadcast logic lives in `RideMatchingCoordinator`:

1. `handleNoCandidates` calls `tryStartBroadcast(...)`. If criteria pass, the request status flips to `BROADCASTING`, notifications are sent to all eligible drivers, and a timeout task is scheduled via `matchingScheduler`.
2. Each `MatchingSession` tracks broadcast state (`Phase.BROADCASTING`, notified driver ids, deadline, timeout future).
3. The first driver to respond runs through `beginBroadcastAcceptance(requestId, driverId)`, which atomically lock-ins the winner and moves the phase to `AWAITING_CONFIRMATION`.
4. If the scheduled timeout fires before any acceptance, `handleBroadcastTimeout` marks the session expired and reuses the normal expiry path (wallet release + rider notification).

### B.5 Service-Level Acceptance

`SharedRideRequestServiceImpl.acceptBroadcast(...)` performs:

| Step | Reason |
|------|--------|
| Validate driver profile (`ACTIVE`) and ownership of the chosen vehicle | Prevent cross-account claims. |
| Double-check request still `BROADCASTING` | Guards against stale clients. |
| Create a new `SharedRide` seeded with pickup/drop-off from the request | Broadcast has no pre-existing ride. |
| Clamp passenger capacity (`vehicle.capacity` or driver default, minimum 1) | Ensures consistent booking math. |
| Update the request to `CONFIRMED`, associate the new ride, stamp `estimatedPickupTime` | Mirrors classic acceptance semantics. |
| Build an ad-hoc `RideMatchProposalResponse` and call `completeBroadcastAcceptance` | Allows the coordinator to finish the workflow and notify the rider. |

On any failure, `failBroadcastAcceptance` clears the lock so another driver can still claim within the remaining window.

### B.6 Status & Timer Interactions

- `SharedRideRequestStatus.BROADCASTING` is used only during the fallback window. It collapses back to `PENDING` (failure) or advances to `CONFIRMED` (success).
- `MatchingSession.markExpiredFromBroadcast()` ensures any outstanding timeout future is cancelled before the request transitions to `EXPIRED`.
- Caller initiated cancellations route through `matchingCoordinator.cancelMatching(...)`, which now also stops broadcast timers.

### B.7 API Surface

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/ride-requests/{id}/broadcast/accept` | `POST` | Driver accepts the broadcast. Body: `{ "vehicleId": ... }`. |

The request DTO is `BroadcastAcceptRequest`. Any existing client using the sequential accept endpoint is unaffected; the mobile app only calls this when the payload indicates `broadcast = true`.

### B.8 Observability and Logging Notes

- Every broadcast attempt logs the number of drivers notified and whether a timeout occurred.
- Acceptance success/failure includes the request id, driver id, and new ride id (when applicable). These breadcrumbs are critical when diagnosing race conditions or disputes.
- Metrics (not yet wired) should increment counters for `broadcast.started`, `broadcast.accepted`, and `broadcast.timeout` once the monitoring story is in place.

Keep this appendix alongside Appendix A so future contributors can trace both the baseline matching flow and the broadcast extension without spelunking into the code.

---

## Appendix C - Ride Lifecycle Automation

### C.1 Configuration Surface

Automation is driven by `app.ride.autoLifecycle.*` (defined in `RideConfigurationProperties.AutoLifecycle`):

| Property | Default | Purpose |
|----------|---------|---------|
| `enabled` | `true` | master switch for the worker |
| `scan-interval-ms` | `60000` | polling frequency for stale rides/requests |
| `ride-auto-start-leeway` | `PT5M` | grace window after `scheduled_time` before auto-start |
| `ride-auto-complete-leeway` | `PT15M` | max age of `startedAt` before auto-complete |
| `request-pickup-timeout` | `PT5M` | max wait in `CONFIRMED` before request auto-start |
| `request-dropoff-timeout` | `PT15M` | max time in `ONGOING` before request auto-complete |

All values live in `application.properties`; adjust per deployment without code changes.

### C.2 Repositories & Queries

- `SharedRideRepository.findScheduledForAutoStart(cutoff)` → rides still `SCHEDULED` past the leeway window.  
- `SharedRideRepository.findOngoingForAutoCompletion(cutoff)` → rides `ONGOING` longer than allowed.  
- `SharedRideRequestRepository.findConfirmedForAutoStart(cutoff)` → requests stuck in `CONFIRMED`.  
- `SharedRideRequestRepository.findOngoingForAutoCompletion(cutoff)` → requests stuck in `ONGOING`.  
- `SharedRideRequestRepository.existsBySharedRideSharedRideIdAndStatusIn(...)` → guard to block ride auto-completion while any request remains active.

### C.3 `RideLifecycleWorker`

Located under `com.mssus.app.worker`, scheduled with the configured fixed delay. It runs four passes per scan:

1. **Auto-start rides**  
   - Locks each ride, ensures confirmed passengers exist, calls `rideRepository.save` to flip status, and kicks off `RideTrackingService.startTracking`.  
   - Logs the ride id, original scheduled time, and passenger count.

2. **Auto-start ride requests**  
   - Promotes stale `CONFIRMED` requests to `ONGOING`, stamping `actualPickupTime`.  
   - Keeps the change lightweight—no notification yet, but logs rider/ride identifiers.

3. **Auto-complete ride requests**  
   - Calls existing `SharedRideService.completeRideRequestOfRide`, passing a synthetic `Authentication` built from the driver user.  
   - Reuses wallet capture / commission logic; errors are caught and logged with request + ride ids.

4. **Auto-complete rides**  
   - Skips rides still holding `CONFIRMED/ONGOING` requests.  
  - Invokes `SharedRideService.completeRide` under the same synthetic auth, with debug logging of failure causes.

An `AtomicBoolean` gate avoids overlapping executions if a scan takes longer than the interval.

### C.4 Authentication Strategy

Automation reuses service-layer guards by forging a `UsernamePasswordAuthenticationToken`:

- Principal: driver’s email.  
- Authorities: `ROLE_DRIVER` and `ROLE_SYSTEM_AUTOMATION` sentinel.  
- Allows existing `@PreAuthorize("hasRole('DRIVER')")` checks to pass without changing controller/service APIs.

### C.5 Logging & Observability

- Key log lines are `info` when actions succeed and `warn` with context when they fail (ride/request id, timestamps).  
- Additional `debug` entries include exception stack traces for troubleshooting.  
- Consider surfacing metrics in future (`ride.autostart.count`, `ride.autocomplete.failures`, etc.).
- Drivers and riders receive real-time websocket + persisted notifications for each automated transition (`*_AUTO_*` notification types), ensuring transparency even when no manual action occurred.

### C.6 Safety Guards & Extensibility

- Manual flows remain authoritative—automation only nudges rides stuck past configurable thresholds.  
- If desired, disable automation via `app.ride.autoLifecycle.enabled=false` or widen leeway durations.  
- Future extensions: push notifications when automation intervenes, partial refunds for auto-completed requests, or GPS distance checks before auto-completion (hook into `RideTrackingService.computeDistanceFromPoints`).


### TODO: Enhance automation ride lifecycle worker service to check driver telemetry via RideTrackingService and decide between auto-start, auto-cancel, or escalation

1. Configuration knobs
   Add two more properties under app.ride.autoLifecycle:
   pickup-proximity-threshold-meters (e.g. 120 m default)
   pickup-proximity-grace-minutes (e.g. 3–5 minutes)
   These let ops tune how strict the geo check is versus the existing time-based auto-start.
2. Repository signal
   Extend SharedRideRequestRepository with a query that returns CONFIRMED requests whose pickupTime is already within the grace window and where the ride is ONGOING. No change to existing indexes.
3. Watchdog pass inside RideLifecycleWorker
   Add a new loop before the current auto-start block:
   For each candidate request, pull the driver's latest position using rideTrackingService.getLatestPosition(rideId, maxStaleMinutes) (reuse the 3‑minute cache).
   Run a Haversine check (PolylineDistance.haversineMeters) against the request pickup coordinates.
   Branch:
   Within threshold → treat as "driver arrived", fall back to today's auto-start path (mark request ONGOING, notify)
   Outside threshold but still inside the grace minutes → just log and possibly send a reminder notification ("Driver is not near rider yet")
   Outside threshold beyond grace → auto-cancel the request: set status EXPIRED (or a new NO_SHOW if you prefer), release wallet hold, notify rider (and optionally re-enqueue for matching/broadcast). Also emit a driver notification warning about potential penalties; future enhancements could flag repeated offenses.
   This logic means the existing unconditional auto-start never runs if the proximity check fails; instead we either wait longer or cancel.
4. Notifications & audit trail
   Reuse notifyRideRequestTransition but add new NotificationType values (REQUEST_AUTO_NO_SHOW, REQUEST_AUTO_REMINDER) so both rider and driver see why the request changed state. Log the computed distance and driver coordinates to help debug.
5. Ecosystem impact
   Manual controls still work; if the driver eventually presses "start", we bypass the watchdog
   The worker's timing currently runs every minute; that's fine for the new proximity loop
   Since we're consuming tracking data, ensure the tracking service is started when the ride goes ONGOING (we already call rideTrackingService.startTracking). Consider adding a fallback (if no GPS points were recorded, treat as "far")
   Wire in optional escalation: after an auto-cancel, publish a domain event so matching can re-broadcast or alert support

Document changes to thresholds or enforcement logic here to keep audit trails clear.
