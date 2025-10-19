# MobileCallBackendFlow

This guide translates the shared-ride backend into the concrete things the mobile apps (rider & driver) must call, listen to, and display. It focuses on two flows: (a) AI-matched shared ride booking, and (b) joining a specific shared ride that already exists. Everything below assumes the caller holds a valid JWT (same token is reused for HTTP and STOMP over WebSocket).

---

## Core Channels Mobile Must Wire Up
- **REST base URL**: `https://<backend-host>/api/v1`.
- **STOMP/WebSocket endpoint**: connect to `wss://<backend-host>/ws` with JWT in the STOMP `Authorization: Bearer <token>` header. Use the `/user` prefix when subscribing to queues.
- **Driver user queue**: subscribe to `/user/queue/ride-offers` to receive ride offers, join requests, and tracking-start signals.
- **Rider user queue**: subscribe to `/user/queue/ride-matching` to receive booking status updates (`ACCEPTED`, `NO_MATCH`, `JOIN_REQUEST_FAILED`, etc.).
- **Push (FCM)**: register one token per signed-in device via `POST /api/v1/fcm/register` `{ "token": "...", "deviceType": "ANDROID|IOS" }`. Token deactivation uses `DELETE /api/v1/fcm/deactivate?token=...`. Tokens are stored per user and reused for every real-time notification.

Backend components behind the scenes:
- `RealTimeNotificationServiceImpl` pushes rich payloads to the queues above and cascades the same event through FCM via `NotificationServiceImpl` → `FcmServiceImpl`.
- `DriverDecisionGateway` enforces an acceptance timeout (from `RideConfigurationProperties.matching.driverResponseSeconds`; default is 30 s unless the config is changed). Mobile UIs should show a countdown using the `offerExpiresAt` field present in driver offer payloads.
- `RideAutoStarter` checks every minute for scheduled rides that should now be `ONGOING` and triggers GPS tracking via `RideTrackingService.startTracking(rideId)`, which sends the `TRACKING_START` push/websocket signal to the driver device.

---

## Shared Ride Booking (AI Match Flow)

### Rider app – booking lifecycle
1. **Quote the trip**  
   - Call `POST /api/v1/quotes` with pickup/drop-off coordinates or location IDs. Response contains a `quoteId` that remains valid for 5 minutes (see `QuoteServiceImpl`).
2. **Submit booking**  
   - Call `POST /api/v1/ride-requests` with body `{ "quoteId": "<UUID>", "desiredPickupTime": "...", "notes": "..." }`.  
   - Backend (`SharedRideRequestServiceImpl.createAIBookingRequest`) validates rider profile, reserves wallet funds, persists a `PENDING` booking, and hands control to `RideMatchingCoordinator.initiateMatching`.
3. **Wait for match updates**  
   - Continue listening on `/user/queue/ride-matching`. `MatchingResponseAssembler` builds the payload:
     - `status: "ACCEPTED"` with ride/driver metadata when a driver accepts.
     - `status: "NO_MATCH"` if every candidate timed out or declined.
   - Optional polling: `GET /api/v1/ride-requests/{requestId}` to refresh current state or show history.
4. **Handle outcomes**  
   - On `ACCEPTED`: store the returned `rideId`, update UI, and prompt rider to be ready at `estimatedPickupTime`.  
   - On `NO_MATCH`: the backend releases the wallet hold automatically; encourage rider to re-book with a new quote.

### Driver app – responding to offers
1. **Always-on setup** on sign-in: register FCM token, connect to `/ws`, and subscribe to `/user/queue/ride-offers`.
2. **Receiving an offer**  
   - `RealTimeNotificationServiceImpl.notifyDriverOffer` delivers `DriverRideOfferNotification`. It contains `requestId`, `rideId` (what the driver would host), rider pickup/drop-off info, `proposalRank`, and `offerExpiresAt`. If the WebSocket connection is down, the same payload is wrapped into an FCM push.
3. **Decision window**  
   - Before the deadline, hit the appropriate endpoint:
     - Accept: `POST /api/v1/ride-requests/{requestId}/accept` with `{ "rideId": <driverRideId> }`.
     - Reject: `POST /api/v1/ride-requests/{requestId}/reject?reason=<text>`.
   - `RideMatchingCoordinator.beginDriverAcceptance` locks the offer as soon as the driver presses “Accept,” so double-submissions should be avoided.
4. **After acceptance**  
   - The rider is notified, the request becomes `CONFIRMED`, and the driver should prepare to start the ride when passengers arrive.

---

## Join Existing Shared Ride Flow

### Rider app – asking to join a specific ride
1. **Discover rides**: call `GET /api/v1/rides/available` with optional time window filters to surface scheduled rides with open seats.
2. **Quote the seat**: reuse `POST /api/v1/quotes`—the same quote endpoint is required so pricing matches the ride.
3. **Submit join request**  
   - Call `POST /api/v1/ride-requests/rides/{rideId}` with `{ "quoteId": "<UUID>", "desiredPickupTime": "...", "notes": "..." }`.
   - Backend (`SharedRideRequestServiceImpl.requestToJoinRide`) locks the ride row, validates seat availability, holds funds, stores the `PENDING` join request, and calls `RideMatchingCoordinator.initiateRideJoining`.
4. **Watch for confirmation**  
   - Continue listening on `/user/queue/ride-matching`:
     - On driver approval, the rider receives `status: "ACCEPTED"` along with ride details.
     - On timeout or driver decline, the rider receives `status: "JOIN_REQUEST_FAILED"` with a reason string; funds are released automatically.

### Driver app – handling join requests
1. Join requests arrive on the same `/user/queue/ride-offers` destination with a `proposalRank` of `1` (see `MatchingResponseAssembler.toDriverJoinRequest`).
2. Accept or reject using the same endpoints as AI offers (`/accept` or `/reject`).  
   - Acceptance keeps the rider on the existing ride, while rejection flips the request to `CANCELLED` and releases the rider’s wallet hold.

---

## Location Tracking Lifecycle (Driver)

1. **Tracking start signal**  
   - Triggered when a ride enters `ONGOING`. This can happen in two ways:
     - Driver manually calls `POST /api/v1/rides/{rideId}/start` with body `{ "rideId": ..., "currentDriverLocation": { "latitude": ..., "longitude": ... } }`. (Method lives in `SharedRideController.startRide` and sets the ride status through `SharedRideServiceImpl.startRide`.)  
     - Scheduled auto-start (`RideAutoStarter.autoStartScheduledRides`) flips `SCHEDULED` rides to `ONGOING` if the driver forgot to do it manually.
   - Once `RideTrackingService.startTracking(rideId)` runs, `RealTimeNotificationServiceImpl.notifyDriverTrackingStart` sends:
     - STOMP payload on `/user/queue/ride-offers`: `{ "type": "TRACKING_START", "rideId": <id>, ... }`
     - FCM push with `data.type = "START_TRACKING"` and `data.rideId`.
2. **Mobile reaction**  
   - Use the FCM or WebSocket signal to start the platform-specific foreground location service. Sample pseudo-code:

```java
// Android
class MyFirebaseMessagingService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage message) {
        if ("START_TRACKING".equals(message.getData().get("type"))) {
            int rideId = Integer.parseInt(message.getData().get("rideId"));
            startForegroundService(
                new Intent(this, GpsTrackingService.class)
                    .putExtra("rideId", rideId)
            );
        } else if ("STOP_TRACKING".equals(message.getData().get("type"))) {
            stopService(new Intent(this, GpsTrackingService.class));
        }
    }
}
```

3. **Streaming GPS points**  
   - While the location service is running, batch points and POST them to `POST /api/v1/rides/rides/{rideId}/track`. The body is a JSON array of `LocationPoint`:

```json
[
  { "lat": 10.763123, "lng": 106.682456, "timestamp": "2025-03-14T08:15:00" },
  { "lat": 10.764210, "lng": 106.684321, "timestamp": "2025-03-14T08:15:20" }
]
```

   - `RideTrackingServiceImpl.appendGpsPoints` verifies that the caller is the ride owner, checks point quality, appends the data, and returns `{ "currentDistanceKm": <double>, "etaMinutes": <int>, "status": "OK" }`.
   - Recommended cadence: send either every 5 points or every 30 seconds—mirrors the example buffer in the repository comments.
4. **Stopping tracking**  
   - When the ride moves to `COMPLETED` (`POST /api/v1/rides/{rideId}/complete`), the backend should emit a `STOP_TRACKING` push; shut down the location service and cease `/track` calls immediately.

---

## Reference Endpoint Summary

| Actor | Action | HTTP verb + path | Notes |
|-------|--------|------------------|-------|
| Rider | Request quote | `POST /api/v1/quotes` | Required before any booking or join request. |
| Rider | AI booking | `POST /api/v1/ride-requests` | Supplies quoteId + optional pickup time. |
| Rider | Join ride | `POST /api/v1/ride-requests/rides/{rideId}` | Uses same quote workflow. |
| Rider | Inspect request | `GET /api/v1/ride-requests/{requestId}` | Can be polled for status. |
| Driver | Accept request | `POST /api/v1/ride-requests/{requestId}/accept` | Body `{ "rideId": ... }`. |
| Driver | Reject request | `POST /api/v1/ride-requests/{requestId}/reject?reason=...` | Releases wallet hold for join requests. |
| Driver | Start ride | `POST /api/v1/rides/{rideId}/start` | Include the same `rideId` in the body payload. |
| Driver | Append GPS | `POST /api/v1/rides/rides/{rideId}/track` | Sends array of `LocationPoint`. |
| Driver | Complete ride | `POST /api/v1/rides/{rideId}/complete` | Captures fares and ends ride. |
| Driver | Register FCM | `POST /api/v1/fcm/register` | Keep token fresh after login. |

---

## Mobile Implementation Checklist
- Maintain both STOMP subscription and FCM listener; every ride-critical event is emitted on both, and one serves as a fallback for the other.
- Surface countdown timers on driver offers; once `offerExpiresAt` is past or a timeout push is received, disable action buttons.
- For riders, rely on the WebSocket message status to decide whether to show navigation to the driver or prompt a retry.
- Log and debounce `/track` uploads; the backend enforces speed and ordering checks, so avoid sending unordered timestamps or empty batches.
- When backgrounding the app, keep the foreground location service (Android) or background location updates (iOS) alive until `STOP_TRACKING` arrives or the ride is marked `COMPLETED`.

Following these steps ensures the mobile apps stay in lockstep with the backend coordination handled by `RideMatchingCoordinator`, `SharedRideRequestServiceImpl`, and the real-time notification stack.
