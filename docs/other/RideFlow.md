# Ride Module Business Flow Documentation

**Version**: 1.0.0 (MVP)  
**Date**: October 4, 2025  
**Status**: Implementation Complete  
**Module**: Shared Ride Booking & Matching

---

## Table of Contents

1. [Overview](#overview)
2. [Dual Flow Architecture](#dual-flow-architecture)
3. [User Flows](#user-flows)
4. [Price Transparency](#price-transparency)
5. [State Machines](#state-machines)
6. [Business Rules](#business-rules)
7. [API Endpoints](#api-endpoints)

---

## Overview

The Ride Module enables **shared motorcycle rides** between students at universities. It supports two booking flows:

- **AI_BOOKING**: Rider creates a request, system proposes matching rides, driver accepts
- **JOIN_RIDE**: Rider browses available rides and requests to join a specific one

### Key Features
- ✅ Quote-based pricing (transparent, no surprises)
- ✅ AI matching algorithm with scoring
- ✅ Wallet holds & captures
- ✅ Cancellation fees with grace periods
- ✅ Real-time seat availability
- ✅ Pessimistic locking for concurrency

---

## Dual Flow Architecture

### Flow 1: AI_BOOKING (AI-Matched Ride)

```
┌─────────┐                 ┌─────────┐                 ┌─────────┐
│  RIDER  │                 │ SYSTEM  │                 │ DRIVER  │
└────┬────┘                 └────┬────┘                 └────┬────┘
     │                           │                           │
     │ 1. GET /quotes            │                           │
     ├──────────────────────────>│                           │
     │   (pickup, dropoff)        │                           │
     │                           │ 2. OSRM routing +         │
     │                           │    pricing calculation    │
     │<──────────────────────────┤                           │
     │   Quote{id, fare, expiry} │                           │
     │                           │                           │
     │ 3. POST /ride-requests    │                           │
     ├──────────────────────────>│                           │
     │   {quoteId, locations}    │                           │
     │                           │ 4. Validate quote         │
     │                           │    NO wallet hold yet     │
     │<──────────────────────────┤                           │
     │   Request{id, PENDING}    │                           │
     │                           │                           │
     │ 5. GET /matches           │                           │
     ├──────────────────────────>│                           │
     │                           │ 6. Run AI matching        │
     │                           │    (proximity, time,      │
     │                           │     rating, detour)       │
     │<──────────────────────────┤                           │
     │   [Proposals sorted       │                           │
     │    by match score]        │                           │
     │                           │                           │
     │                           │ 7. Driver views request   │
     │                           │<──────────────────────────┤
     │                           │   GET /ride-requests/     │
     │                           │       rides/{rideId}      │
     │                           │                           │
     │                           │ 8. Driver accepts         │
     │                           │<──────────────────────────┤
     │                           │   POST /accept            │
     │                           │   {rideId, pickupTime}    │
     │                           │                           │
     │                           │ 9. Assign shared_ride_id  │
     │                           │    Place wallet HOLD      │
     │                           ├──────────────────────────>│
     │                           │   Request CONFIRMED       │
     │<──────────────────────────┤                           │
     │   Request{CONFIRMED}      │                           │
     └───────────────────────────┴───────────────────────────┘
```

**Key Points:**
- No wallet hold until driver accepts (prevents locking funds during matching)
- `shared_ride_id` is NULL initially, assigned on acceptance
- AI matching runs on-demand when rider requests proposals

---

### Flow 2: JOIN_RIDE (Direct Join)

```
┌─────────┐                 ┌─────────┐                 ┌─────────┐
│  RIDER  │                 │ SYSTEM  │                 │ DRIVER  │
└────┬────┘                 └────┬────┘                 └────┬────┘
     │                           │                           │
     │ 1. GET /rides/available   │                           │
     ├──────────────────────────>│                           │
     │   (time window filter)    │                           │
     │<──────────────────────────┤                           │
     │   [Available rides list]  │                           │
     │                           │                           │
     │ 2. GET /quotes            │                           │
     ├──────────────────────────>│                           │
     │   (pickup, dropoff)        │                           │
     │<──────────────────────────┤                           │
     │   Quote{id, fare}         │                           │
     │                           │                           │
     │ 3. POST /rides/{id}/      │                           │
     │    requests               │                           │
     ├──────────────────────────>│                           │
     │   {quoteId, locations}    │                           │
     │                           │ 4. Validate: quote,       │
     │                           │    seat availability      │
     │                           │    Place wallet HOLD      │
     │<──────────────────────────┤                           │
     │   Request{PENDING}        │                           │
     │                           │                           │
     │                           │ 5. Notify driver          │
     │                           ├──────────────────────────>│
     │                           │   New request available   │
     │                           │                           │
     │                           │ 6. Driver accepts         │
     │                           │<──────────────────────────┤
     │                           │   POST /accept            │
     │                           │                           │
     │<──────────────────────────┤                           │
     │   Request{CONFIRMED}      │                           │
     └───────────────────────────┴───────────────────────────┘
```

**Key Points:**
- Wallet hold placed immediately on request creation
- `shared_ride_id` is set from the start
- Rider sees driver details before requesting

---

## User Flows

### Rider Flow: Book a Ride

**Pre-conditions**: Rider authenticated, sufficient wallet balance

**Steps**:
1. **Get Quote**: `POST /api/v1/quotes`
   - Input: `{pickup: {lat, lng}, dropoff: {lat, lng}}`
   - Output: `{quoteId, fare, distance, duration, expiresAt}`
   - System: OSRM routing → pricing calculation → cache quote (5min TTL)

2. **Create AI Booking**: `POST /api/v1/ride-requests`
   - Input: `{quoteId, pickupLocationId, dropoffLocationId, pickupTime, notes}`
   - Validation:
     - Quote exists & not expired
     - Rider owns quote
     - Locations match quote coordinates (±100m tolerance)
   - Output: `{requestId, status: PENDING, fare}`
   - State: `AI_BOOKING`, `shared_ride_id = NULL`

3. **View Match Proposals**: `GET /api/v1/ride-requests/{requestId}/matches`
   - System runs matching algorithm
   - Returns sorted proposals by match score
   - Each proposal includes: driver rating, vehicle, detour, estimated times

4. **Wait for Driver Acceptance** (passive)
   - Driver accepts → request becomes `CONFIRMED`
   - Wallet hold placed automatically
   - Rider receives notification

5. **Ride Day**:
   - Driver starts ride → request becomes `ONGOING`
   - Driver completes ride → wallet captured → request becomes `COMPLETED`

---

### Driver Flow: Accept a Request

**Pre-conditions**: Driver authenticated, ride created in SCHEDULED state

**Steps**:
1. **View Pending Requests**: `GET /api/v1/ride-requests/rides/{rideId}?status=PENDING`
   - See all pending requests for their ride
   - For AI_BOOKING: see match score, rider rating
   - For JOIN_RIDE: see pickup/dropoff locations

2. **Accept Request**: `POST /api/v1/ride-requests/{requestId}/accept`
   - Input: `{rideId, estimatedPickupTime}`
   - Validation:
     - Driver owns `rideId`
     - Request is `PENDING`
     - Seats available
   - Actions:
     - For AI_BOOKING: assign `shared_ride_id`, place wallet hold
     - For JOIN_RIDE: confirm request (hold already placed)
     - Increment `current_passengers`
   - Output: `{requestId, status: CONFIRMED}`

3. **Reject Request** (optional): `POST /api/v1/ride-requests/{requestId}/reject?reason=...`
   - For JOIN_RIDE: releases wallet hold
   - Request becomes `CANCELLED`

---

## Price Transparency

### Quote-Based Pricing (Prevents Jail Time! 😄)

**Problem**: Dynamic pricing without upfront quotes can lead to legal issues.

**Solution**: **Quote-First Architecture**

```
All ride requests MUST include a quoteId from the pricing service.

Flow:
1. Rider requests quote (valid 5 minutes)
2. System: OSRM routing → distance/duration → pricing algorithm → quote
3. Quote cached with fare breakdown
4. Rider uses quoteId in booking request
5. System validates:
   - Quote exists & not expired
   - Rider owns quote
   - Locations match quote coordinates
6. Fare locked in from quote
```

**Validation Rules**:
- Location tolerance: ±100 meters (~0.001 degrees)
- Quote expiry: 5 minutes
- Ownership check: `quote.riderId == authenticated_user.id`

**Benefits**:
- ✅ Transparent pricing (rider sees exact fare before booking)
- ✅ Legal compliance (no surprise charges)
- ✅ Prevents price manipulation
- ✅ Audit trail (quote ID in request)

---

## State Machines

### SharedRide Status Machine

```
       ┌──────────┐
       │SCHEDULED │
       └─────┬────┘
             │
      START  │
             ▼
       ┌──────────┐
       │ ONGOING  │
       └─────┬────┘
             │
    COMPLETE │
             ▼
       ┌──────────┐
       │COMPLETED │
       └──────────┘

       ┌──────────┐
       │SCHEDULED │
       └─────┬────┘
             │
     CANCEL  │
             ▼
       ┌──────────┐
       │CANCELLED │
       └──────────┘
```

**Valid Transitions**:
- SCHEDULED → ONGOING: Driver starts ride (requires ≥1 CONFIRMED request)
- ONGOING → COMPLETED: Driver completes ride (captures fares)
- SCHEDULED → CANCELLED: Driver/Admin cancels (releases holds)

**Immutable States**: COMPLETED, CANCELLED

---

### SharedRideRequest Status Machine

```
AI_BOOKING Flow:
   ┌─────────┐
   │ PENDING │ (shared_ride_id = NULL)
   └────┬────┘
        │ Driver accepts
        ▼
   ┌───────────┐
   │ CONFIRMED │ (shared_ride_id assigned, hold placed)
   └─────┬─────┘
         │ Ride starts
         ▼
   ┌─────────┐
   │ ONGOING │
   └────┬────┘
        │ Ride completes
        ▼
   ┌───────────┐
   │ COMPLETED │
   └───────────┘

JOIN_RIDE Flow:
   ┌─────────┐
   │ PENDING │ (shared_ride_id set, hold placed)
   └────┬────┘
        │ Driver accepts
        ▼
   ┌───────────┐
   │ CONFIRMED │
   └─────┬─────┘
         │ (same as above)

Terminal States:
   - CANCELLED (rider/driver cancels)
   - EXPIRED (timeout, no driver accepted)
```

**Valid Transitions**:
- PENDING → CONFIRMED: Driver accepts
- PENDING → CANCELLED: Rider/Driver cancels, Driver rejects
- PENDING → EXPIRED: Timeout (T_ACCEPT = 5 minutes)
- CONFIRMED → ONGOING: Ride starts
- CONFIRMED → CANCELLED: Rider cancels (may incur fee)
- ONGOING → COMPLETED: Ride completes
- ONGOING → CANCELLED: Admin cancels (edge case)

---

## Business Rules

### BR-25: Proximity Matching
- Pickup/dropoff must be within **2.0 km** of ride route
- Haversine distance calculation
- Configurable: `app.ride.matching.maxProximityKm`

### BR-26: Time Window Matching
- Candidate rides must be scheduled within **±15 minutes** of requested pickup
- Configurable: `app.ride.matching.timeWindowMinutes`

### BR-27: Detour Limits
- Maximum detour: **8 minutes** (driver preference)
- Driver can set own limit (1-30 minutes)
- Configurable: `app.ride.matching.maxDetourMinutes`

### BR-28: Cancellation Fees
- **Grace Period**: 2 minutes after confirmation (free cancellation)
- **After Grace**: 20% cancellation fee
- Fee captured from wallet hold
- Configurable: `app.ride.cancellation.feePercentage`, `app.ride.cancellation.gracePeriodMinutes`

### BR-29: Request Timeout
- **T_ACCEPT**: 5 minutes for driver to accept
- After timeout: request → EXPIRED
- Scheduled job checks every minute
- Configurable: `app.ride.requestAcceptTimeout`

### BR-30: Seat Concurrency
- **Pessimistic Locking**: `SELECT FOR UPDATE` on ride
- Atomic seat check + increment
- Prevents double-booking race conditions

---

## API Endpoints

### Summary

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/rides` | POST | DRIVER | Create ride |
| `/rides/{id}` | GET | ANY | Get ride details |
| `/rides/driver/{id}` | GET | DRIVER/ADMIN | List driver's rides |
| `/rides/available` | GET | RIDER | Browse available rides |
| `/rides/{id}/start` | POST | DRIVER | Start ride |
| `/rides/{id}/complete` | POST | DRIVER | Complete ride |
| `/rides/{id}` | DELETE | DRIVER/ADMIN | Cancel ride |
| `/ride-requests` | POST | RIDER | Create AI booking |
| `/ride-requests/rides/{id}` | POST | RIDER | Join specific ride |
| `/ride-requests/{id}` | GET | ANY | Get request details |
| `/ride-requests/{id}/matches` | GET | RIDER | Get match proposals |
| `/ride-requests/rider/{id}` | GET | RIDER/ADMIN | List rider's requests |
| `/ride-requests/rides/{id}` | GET | DRIVER/ADMIN | List ride's requests |
| `/ride-requests/{id}/accept` | POST | DRIVER | Accept request |
| `/ride-requests/{id}/reject` | POST | DRIVER | Reject request |
| `/ride-requests/{id}` | DELETE | RIDER/ADMIN | Cancel request |

**Total**: 16 endpoints (13 core + 3 query variants)

---

## Future Enhancements (Post-MVP)

### Phase 2: Advanced Matching
- [ ] Corridor analysis with PostGIS/JTS
- [ ] OSRM integration for accurate detour calculation
- [ ] ML-based scoring with historical data
- [ ] Real-time traffic adjustments

### Phase 3: Real-Time Features
- [ ] WebSocket for live updates
- [ ] Driver location streaming
- [ ] Dynamic ETA calculations
- [ ] Instant notifications (FCM, Twilio)

### Phase 4: Optimization
- [ ] Promotion/discount system
- [ ] Multi-rider route optimization
- [ ] Predictive matching
- [ ] Demand heatmaps

---

## References

- [Implementation Plan](./RideImplementationPlan.md)
- [Implementation Log](./RideImplementationLog.md)
- [API Documentation](../api/)
- [Architecture Diagrams](../architecture/)

---

**Document Owner**: Backend Team  
**Last Updated**: October 4, 2025  
**Review Cycle**: Quarterly

---

## Appendix A - Ride Lifecycle Method Reference

This appendix documents the four lifecycle methods introduced to keep ride-level state management decoupled from rider-level progression. Use it when reviewing code paths, auditing incidents, or onboarding new contributors.

### `startRide`

- **Who calls it**: Driver (service entry point `SharedRideService.startRide`)
- **When it is allowed**: The shared ride is in `SCHEDULED`.
- **What it does**: Locks the ride, logs the driver’s current position, then flips the ride status to `ONGOING` and stamps `startedAt`. It does *not* touch individual ride requests.
- **Why it matters**: Keeps the ride’s timeline authoritative while leaving per-rider transitions to separate methods, preventing accidental double updates.

### `startRideRequestOfRide`

- **Who calls it**: Driver after collecting a specific rider (`SharedRideService.startRideRequestOfRide`)
- **When it is allowed**: Ride is already `ONGOING`, the targeted request belongs to the ride, and sits in `CONFIRMED`. The driver must still be within pickup proximity of that rider.
- **What it does**: Marks the request `ONGOING`, records `actualPickupTime`, and returns a refreshed `SharedRideRequestResponse`.
- **Why it matters**: Records proof-of-pickup per rider, supports staggered pickups, and keeps rider analytics correct even if the ride has multiple legs.

### `completeRide`

- **Who calls it**: Driver when the entire route is finished (`SharedRideService.completeRide`)
- **When it is allowed**: Ride is `ONGOING`, all outstanding ride requests have been moved out of `ONGOING`, and settlement can be computed.
- **What it does**: Calculates route distance/duration, performs wallet captures and commissions, updates ride totals, sets status to `COMPLETED`, and emits completion stats.
- **Why it matters**: Centralizes financial closure for the ride while relying on per-request completion to guarantee each rider’s fare is already validated.

### `completeRideRequestOfRide`

- **Who calls it**: Driver as each rider is dropped off (`SharedRideService.completeRideRequestOfRide`)
- **When it is allowed**: Ride is `ONGOING`, request is `ONGOING`, and the driver is within drop-off proximity for that passenger.
- **What it does**: Marks the request `COMPLETED`, updates actual metrics, and (once implemented) can trigger partial captures or notifications.
- **Why it matters**: Enforces granular accountability—each rider’s journey, fare capture, and metrics are recorded independently, supporting disputed trips and post-ride analytics.

### Design Outcome

By separating ride-level and request-level methods:

- **State Clarity**: Avoids hidden coupling between ride and rider state machines, making domain rules explicit.
- **Concurrency Safety**: Each method performs a tailored lock/read/validate cycle, reducing race-condition surface area.
- **Extensibility**: Future enhancements (e.g., partial refunds, per-rider notifications) can plug into the dedicated methods without risking regressions elsewhere.
- **Observability**: Logs and metrics can distinguish between “ride started” and “rider picked up,” improving monitoring fidelity.

Keep this appendix updated whenever lifecycle semantics evolve so future maintainers understand the rationale behind split methods.

### Why the Separation Matters (A Story From The Road)

**Shared ride and shared ride request story.**
Shared ride is "driver intended to go from A to B, and on that route, they are willing to share their ride with someone who wants to move along that route". Shared ride request is "rider wants to go from where he is standing to a desired destination through a ride shared by a driver who is also going on that route". So, the ride matching flow is essentially "if rider's place of standing is on driver's intended route or detour in an acceptable range, then a shared ride matching can happen between these people"

**The ideal morning.**  
Minh schedules a shared ride for 6:30 AM from his apartment in Vinhomes Grand Park to FPT University. He hops on his bike, taps **Start Ride**, and the platform marks his route as `ONGOING`. Five minutes later a broadcast goes out: An is standing along the corridor at gate C, wanting to join. Minh accepts, drives toward An, taps **Start Ride Request** when he picks her up, and the two of them ride happily to campus. When they arrive, Minh drops An off, taps **Complete Ride Request**, and finally **Complete Ride** once he finishes his own journey. Every event—Minh’s trip, An’s experience, the wallet captures—lands in the ledger at the correct timestamps.

**What happens when everything is fused.**  
Now imagine we never split the lifecycle. The moment Minh hits **Start Ride**, every `CONFIRMED` request is forced to `ONGOING` too—even if the rider is still waiting. If An cancels because Minh never arrived, both of them lose track of who owes what. Worse, if Minh taps **Complete Ride** after dropping off his first passenger, the system would close the entire ride; An would still be sitting on the curb with a request stuck in `ONGOING`, no way to join later segments, and no protection from automatic wallet captures. In other words, the driver’s story (“I’m driving from A to B”) and the rider’s story (“I need to go from where I stand to my destination”) get hopelessly entangled.

**Independent timelines make the ride pairing work.**  
By keeping `SharedRide` and `SharedRideRequest` states independent we let the matching flow do its real job: determine whether the rider’s standing point fits on the driver’s intended route within an acceptable detour. Once that decision is made, each timeline advances at its own pace—drivers can start their journey, riders can join later or leave earlier, and automation can enforce rules without guesswork. The separation gives us clarity, fairness, and the room to evolve features (broadcast fallback, proactive acceptance, lifecycle automation) without rewriting every edge case.
