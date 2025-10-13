# Ride Module Implementation Plan

**Project:** MSSUS Ride Booking & Sharing Module  
**Date:** October 4, 2025  
**Status:** Awaiting Approval (Updated after feedback)  
**Scope:** MVP implementation of 13 core ride endpoints

**Changelog:**
- Added POST /api/v1/ride-requests (AI booking creation)
- Added DELETE /api/v1/ride-requests/{id} (rider cancel)
- Updated accept endpoint to include rideId in body
- Tightened database constraints for request_kind flows
- Split wallet hold timing by flow type
- Added clarifications for MVP scope (promotion, notification, matching, routing, WebSocket)

---

## Table of Contents

1. [Endpoint Specifications](#1-endpoint-specifications)
2. [Database Schema & Transactions](#2-database-schema--transactions)
3. [RBAC Matrix](#3-rbac-matrix)
4. [State Machines](#4-state-machines)
5. [Exception Mapping](#5-exception-mapping)
6. [Flyway Migrations](#6-flyway-migrations)
7. [Architecture Decisions](#7-architecture-decisions)

---

## 1. Endpoint Specifications

### 1.1 POST /api/v1/rides (Create Shared Ride)

**Role:** Driver  
**Description:** Driver creates a shared ride offering

**Request Body:**
```json
{
  "vehicleId": 15,
  "startLocationId": 10,
  "endLocationId": 11,
  "scheduledTime": "2025-10-05T07:30:00Z",
  "maxPassengers": 1,
  "baseFare": 20000,
  "perKmRate": 3500,
  "estimatedDistance": 5.2,
  "estimatedDuration": 15
}
```

**Response (201 Created):**
```json
{
  "sharedRideId": 300,
  "status": "SCHEDULED",
  "driverId": 50,
  "vehicleId": 15,
  "startLocationId": 10,
  "endLocationId": 11,
  "scheduledTime": "2025-10-05T07:30:00Z",
  "maxPassengers": 1,
  "currentPassengers": 0,
  "baseFare": 20000,
  "perKmRate": 3500,
  "estimatedDistance": 5.2,
  "estimatedDuration": 15,
  "createdAt": "2025-10-04T14:30:00Z"
}
```

**Errors:**
- 400: Invalid request (missing fields, invalid values)
- 401: Unauthorized
- 403: Not a driver / driver not active
- 404: Vehicle or location not found

---

### 1.2 PUT /api/v1/rides/{id} (Update Shared Ride)

**Role:** Driver (own rides), Admin  
**Description:** Update ride details before it has confirmed passengers

**Request Body:**
```json
{
  "scheduledTime": "2025-10-05T08:00:00Z",
  "maxPassengers": 2,
  "baseFare": 22000,
  "perKmRate": 3800
}
```

**Response (200 OK):** Returns updated ride object same as Create

**Errors:**
- 400: Invalid update (has confirmed passengers)
- 401: Unauthorized
- 403: Not owner / not admin
- 404: Ride not found

---

### 1.3 DELETE /api/v1/rides/{id} (Cancel Ride)

**Role:** Driver (own rides), Admin  
**Description:** Cancel a ride; refunds riders if any confirmed

**Request Body (Optional):**
```json
{
  "reason": "Vehicle maintenance required"
}
```

**Response (200 OK):**
```json
{
  "message": "Ride canceled",
  "status": "CANCELLED",
  "affectedRiders": 2
}
```

**Errors:**
- 400: Cannot cancel (already started/completed)
- 401: Unauthorized
- 403: Not owner / not admin
- 404: Ride not found

---

### 1.4 GET /api/v1/rides/{rideId} (Ride Details)

**Role:** Driver (own), Riders (with request in ride), Admin  
**Description:** Get detailed ride information

**Response (200 OK):**
```json
{
  "sharedRideId": 300,
  "status": "SCHEDULED",
  "scheduledTime": "2025-10-05T07:30:00Z",
  "driver": {
    "driverId": 50,
    "fullName": "Tran Van B",
    "ratingAvg": 4.8,
    "phone": "090***4567"
  },
  "vehicle": {
    "vehicleId": 15,
    "model": "Honda Wave",
    "plateNumber": "59A-12345",
    "color": "Red"
  },
  "riders": [
    {
      "riderId": 123,
      "fullName": "Nguyen Van A",
      "status": "CONFIRMED",
      "pickupLocation": {"lat": 10.8700, "lng": 106.8000},
      "dropoffLocation": {"lat": 10.8760, "lng": 106.8020}
    }
  ],
  "startLocation": {"name": "Dorm A", "lat": 10.8700, "lng": 106.8000},
  "endLocation": {"name": "Campus Gate", "lat": 10.8760, "lng": 106.8020},
  "maxPassengers": 1,
  "currentPassengers": 1,
  "baseFare": 20000,
  "perKmRate": 3500
}
```

**Errors:**
- 401: Unauthorized
- 403: Not authorized to view
- 404: Ride not found

---

### 1.5 POST /api/v1/rides/{rideId}/start (Start Ride)

**Role:** Driver (own rides), Admin  
**Description:** Mark ride as started; transitions to ONGOING

**Request Body (Optional):**
```json
{
  "currentLat": 10.8700,
  "currentLng": 106.8000
}
```

**Response (200 OK):**
```json
{
  "sharedRideId": 300,
  "status": "ONGOING",
  "startedAt": "2025-10-05T07:32:00Z"
}
```

**Errors:**
- 400: Cannot start (no confirmed riders, already started, etc.)
- 401: Unauthorized
- 403: Not owner / not admin
- 404: Ride not found

---

### 1.6 POST /api/v1/rides/{rideId}/complete (Complete Ride)

**Role:** Driver (own rides), Admin  
**Description:** Mark ride as completed; capture payments, update stats

**Request Body (Optional):**
```json
{
  "actualDistance": 5.5,
  "currentLat": 10.8760,
  "currentLng": 106.8020
}
```

**Response (200 OK):**
```json
{
  "sharedRideId": 300,
  "status": "COMPLETED",
  "completedAt": "2025-10-05T08:05:00Z",
  "actualDistance": 5.5,
  "fareSummary": [
    {
      "riderId": 123,
      "riderName": "Nguyen Van A",
      "amountCharged": 25000,
      "discount": 5000
    }
  ],
  "driverEarnings": 23750,
  "platformCommission": 2500
}
```

**Errors:**
- 400: Cannot complete (not started, already completed)
- 401: Unauthorized
- 403: Not owner / not admin
- 404: Ride not found
- 500: Payment capture failed

---

### 1.7 GET /api/v1/rides/available (Browse Available Rides)

**Role:** Rider  
**Description:** Browse rides available for joining

**Query Parameters:**
- `pickupLat` (required): Pickup latitude
- `pickupLng` (required): Pickup longitude
- `dropoffLat` (required): Dropoff latitude
- `dropoffLng` (required): Dropoff longitude
- `time` (optional): Desired departure time (default: now)
- `radiusKm` (optional): Search radius (default: 5)
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Response (200 OK):**
```json
{
  "rides": [
    {
      "sharedRideId": 300,
      "driverId": 50,
      "driverName": "Tran Van B",
      "driverRating": 4.8,
      "scheduledTime": "2025-10-05T07:30:00Z",
      "baseFare": 20000,
      "perKmRate": 3500,
      "availableSeats": 1,
      "startLocation": {"name": "Dorm A", "lat": 10.8700, "lng": 106.8000},
      "endLocation": {"name": "Campus Gate", "lat": 10.8760, "lng": 106.8020},
      "estimatedDistance": 5.2,
      "estimatedDuration": 15,
      "vehicle": {
        "model": "Honda Wave",
        "color": "Red",
        "plateNumber": "59A-12345"
      }
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 15,
    "totalPages": 1
  }
}
```

**Errors:**
- 400: Invalid coordinates
- 401: Unauthorized

---

### 1.8 POST /api/v1/rides/{rideId}/requests (Request to Join)

**Role:** Rider  
**Description:** Request to join a specific shared ride

**Request Body:**
```json
{
  "pickupLocationId": 10,
  "dropoffLocationId": 11,
  "pickupTime": "2025-10-05T07:35:00Z",
  "specialRequests": "Please bring extra helmet",
  "promotionCode": "STUDENT10"
}
```

**Response (201 Created):**
```json
{
  "sharedRideRequestId": 400,
  "sharedRideId": 300,
  "status": "PENDING",
  "riderId": 123,
  "pickupLocationId": 10,
  "dropoffLocationId": 11,
  "pickupTime": "2025-10-05T07:35:00Z",
  "fareAmount": 22500,
  "originalFare": 25000,
  "discountAmount": 2500,
  "specialRequests": "Please bring extra helmet",
  "createdAt": "2025-10-04T15:00:00Z"
}
```

**Errors:**
- 400: Invalid request (no seats, incompatible route, insufficient wallet)
- 401: Unauthorized
- 403: Not a rider / rider not active
- 404: Ride not found
- 409: Already have pending/confirmed request for this ride

---

### 1.9 GET /api/v1/ride-requests/available (Driver Browse Rider Requests)

**Role:** Driver  
**Description:** Browse pending rider requests compatible with routes

**Query Parameters:**
- `route` (optional): Encoded polyline of planned route
- `radiusKm` (optional): Distance from route (default: 1 km)
- `minTime` (optional): Minimum pickup time filter
- `maxTime` (optional): Maximum pickup time filter
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 20)

**Response (200 OK):**
```json
{
  "requests": [
    {
      "sharedRideRequestId": 401,
      "riderId": 124,
      "riderName": "Le Thi C",
      "riderRating": 4.9,
      "pickupLocation": {"name": "Dorm B", "lat": 10.8710, "lng": 106.8010},
      "dropoffLocation": {"name": "Library", "lat": 10.8765, "lng": 106.8030},
      "pickupTime": "2025-10-05T07:40:00Z",
      "estimatedFare": 25000,
      "specialRequests": null,
      "createdAt": "2025-10-04T14:50:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 8,
    "totalPages": 1
  }
}
```

**Errors:**
- 400: Invalid route polyline
- 401: Unauthorized
- 403: Not a driver

---

### 1.10 POST /api/v1/ride-requests (Create AI Booking Request)

**Role:** Rider  
**Description:** Create an AI-matched ride request (without specific ride)

**Request Body:**
```json
{
  "pickupLocationId": 10,
  "dropoffLocationId": 11,
  "pickupTime": "2025-10-05T07:35:00Z",
  "specialRequests": "Please bring extra helmet",
  "promotionCode": "STUDENT10"
}
```

**Response (201 Created):**
```json
{
  "sharedRideRequestId": 402,
  "requestKind": "AI_BOOKING",
  "status": "PENDING",
  "riderId": 123,
  "sharedRideId": null,
  "pickupLocationId": 10,
  "dropoffLocationId": 11,
  "pickupTime": "2025-10-05T07:35:00Z",
  "fareAmount": 25000,
  "originalFare": 25000,
  "discountAmount": 0,
  "specialRequests": "Please bring extra helmet",
  "createdAt": "2025-10-04T15:00:00Z"
}
```

**Errors:**
- 400: Invalid request (insufficient wallet for quote)
- 401: Unauthorized
- 403: Not a rider / rider not active
- 404: Location not found

**Note:** Wallet hold is NOT placed at creation; hold happens when driver accepts.

---

### 1.11 POST /api/v1/ride-requests/{requestId}/accept (Accept Ride Request)

**Role:** Driver  
**Description:** Driver accepts a pending ride request (AI_BOOKING or JOIN_RIDE)

**Request Body:**
```json
{
  "rideId": 300,
  "estimatedPickupTime": "2025-10-05T07:35:00Z"
}
```

**Response (200 OK):**
```json
{
  "sharedRideRequestId": 400,
  "status": "CONFIRMED",
  "sharedRideId": 300,
  "driverId": 50,
  "riderId": 123,
  "estimatedPickupTime": "2025-10-05T07:35:00Z",
  "confirmedAt": "2025-10-04T15:05:00Z"
}
```

**Errors:**
- 400: Cannot accept (no seats, timeout, incompatible)
- 401: Unauthorized
- 403: Not the ride owner (driver does not own rideId)
- 404: Request not found or ride not found
- 409: Request already processed
- 500: Wallet hold failed (for AI_BOOKING flow)

**Implementation Notes:**
- For **AI_BOOKING**: Set `shared_ride_id = rideId`, place wallet hold, increment seat count
- For **JOIN_RIDE**: Wallet hold already exists; just confirm and increment seat count
- Validation: Driver must own `rideId`; request must be PENDING; seats must be available

---

### 1.12 POST /api/v1/ride-requests/{requestId}/deny (Deny Ride Request)

**Role:** Driver  
**Description:** Driver denies a pending ride request

**Request Body (Optional):**
```json
{
  "reason": "Route does not match"
}
```

**Response (200 OK):**
```json
{
  "sharedRideRequestId": 400,
  "status": "CANCELLED",
  "deniedBy": 50,
  "reason": "Route does not match",
  "deniedAt": "2025-10-04T15:05:00Z"
}
```

**Errors:**
- 400: Cannot deny (already processed)
- 401: Unauthorized
- 403: Not the ride owner
- 404: Request not found
- 409: Request already processed

---

### 1.13 DELETE /api/v1/ride-requests/{id} (Cancel Ride Request - Rider)

**Role:** Rider (own requests), Admin  
**Description:** Rider cancels their own pending or confirmed ride request

**Request Body (Optional):**
```json
{
  "reason": "Plans changed"
}
```

**Response (200 OK):**
```json
{
  "sharedRideRequestId": 400,
  "status": "CANCELLED",
  "cancelledAt": "2025-10-04T15:10:00Z",
  "refundAmount": 22500,
  "cancellationFee": 2500
}
```

**Errors:**
- 400: Cannot cancel (already started/completed, or past cancellation window)
- 401: Unauthorized
- 403: Not the requester / not admin
- 404: Request not found

**Implementation Notes:**
- **PENDING status**: Full refund (release hold), no fee
- **CONFIRMED status** (before ride starts): Apply late cancellation fee per BR-32 (20% of held fare, max 10,000 VND), release remainder
- **ONGOING/COMPLETED**: Cannot cancel

---

## 2. Database Schema & Transactions

### 2.1 Tables Involved

**Core Tables:**
1. `shared_rides` - Ride listings created by drivers
2. `shared_ride_requests` - Rider requests to join rides
3. `locations` - Pickup/dropoff locations
4. `driver_profiles` - Driver information
5. `rider_profiles` - Rider information
6. `vehicles` - Driver vehicles
7. `wallets` - User wallet balances
8. `transactions` - Financial transactions
9. `ai_matching_logs` - Matching algorithm logs
10. `notifications` - User notifications

### 2.2 Schema Modifications Required

Based on the prompt and business requirements, we need to update the `shared_ride_requests` table to support the dual-flow matching described:

**Current Schema Issues:**
- `shared_ride_requests.shared_ride_id` is currently NOT NULL (needs to be nullable)
- Missing `request_kind` field to distinguish AI_BOOKING vs JOIN_RIDE flows
- Status enum needs alignment with new flow (add EXPIRED)
- Constraint too loose (allows CONFIRMED without shared_ride_id)

**Required Changes:**
1. Add `request_kind` ENUM ('AI_BOOKING', 'JOIN_RIDE')
2. Make `shared_ride_id` nullable (for AI_BOOKING initial state)
3. Update status transitions to support new flow (add EXPIRED)
4. Add tightened constraint ensuring data integrity per flow type
5. Add indexes for matching queries

### 2.3 Transaction Boundaries

#### 2.3.1 Create Shared Ride Transaction
```
BEGIN TRANSACTION
1. Validate driver (active, has active vehicle, no conflicting rides)
2. Validate vehicle (active, insurance valid)
3. Validate locations exist
4. Insert into shared_rides (status=SCHEDULED)
5. Log audit trail
COMMIT
```

**Rollback Conditions:**
- Driver validation fails
- Vehicle validation fails
- Location not found
- Unique constraint violation

---

#### 2.3.2 Request to Join Ride Transaction (JOIN_RIDE flow)
```
BEGIN TRANSACTION
1. Validate ride (exists, has seats, status=SCHEDULED)
2. Validate rider (active, no conflicting requests)
3. Calculate fare using QuoteService
4. Create wallet HOLD (pending_balance += fare)
5. Insert shared_ride_request (status=PENDING, request_kind=JOIN_RIDE, shared_ride_id=rideId)
6. Notify driver
COMMIT
```

**Rollback Conditions:**
- Ride validation fails
- Rider validation fails
- Wallet insufficient balance
- Duplicate request
- Fare calculation error

**Compensation Logic:**
- If notification fails after commit: Log error, retry async
- If hold succeeds but request fails: Release hold immediately

---

#### 2.3.2b Create AI Booking Request Transaction (AI_BOOKING flow)
```
BEGIN TRANSACTION
1. Validate rider (active, no conflicting requests)
2. Validate locations exist
3. Calculate fare estimate using QuoteService (for display only)
4. Insert shared_ride_request (status=PENDING, request_kind=AI_BOOKING, shared_ride_id=NULL)
5. Trigger matching algorithm (async)
COMMIT
```

**Rollback Conditions:**
- Rider validation fails
- Location not found
- Fare calculation error

**Compensation Logic:**
- No wallet hold at this stage; hold happens at accept
- If matching fails: Request moves to EXPIRED status via timeout job

**Note:** Wallet hold is deferred to accept for AI_BOOKING to avoid locking funds during matching

---

#### 2.3.3 Accept Request Transaction
```
BEGIN TRANSACTION (SERIALIZABLE isolation level)
1. Validate driver owns rideId (from request body)
2. Lock shared_ride FOR UPDATE (using rideId)
3. Validate request (status=PENDING)
4. Validate seats available (current_passengers < max_passengers)
5. IF request_kind = AI_BOOKING:
   a. Set shared_ride_id = rideId
   b. Calculate fare using QuoteService
   c. Create wallet HOLD (pending_balance += fare)
   d. Create HOLD_CREATE transaction record
6. ELSE (JOIN_RIDE):
   a. Wallet hold already exists; skip
7. Update shared_ride_request (status=CONFIRMED, estimated_pickup_time, shared_ride_id if AI_BOOKING)
8. Update shared_ride (current_passengers += 1)
9. Notify rider
COMMIT
```

**Rollback Conditions:**
- Driver does not own rideId
- Request not found or not PENDING
- No seats available (race condition)
- Wallet operation fails (AI_BOOKING only)

**Concurrency Control:**
- Use SERIALIZABLE isolation or pessimistic locking
- Check-and-increment seats atomically
- Handle OptimisticLockException with retry

**Split Logic:**
- **AI_BOOKING**: Wallet hold happens HERE (at accept)
- **JOIN_RIDE**: Wallet hold already exists from request creation

---

#### 2.3.4 Complete Ride Transaction
```
BEGIN TRANSACTION
1. Validate ride (status=ONGOING)
2. Fetch all CONFIRMED/ONGOING requests
3. FOR EACH request:
   a. Calculate final fare (may differ from quote)
   b. CAPTURE from rider wallet (pending → available → OUT)
   c. Update request (status=COMPLETED, actual times/fare)
   d. Create transactions (CAPTURE_FARE for rider, driver, commission)
4. Update shared_ride (status=COMPLETED, completed_at, actual_distance)
5. Update driver stats (total_shared_rides++, total_earned+=)
6. Update rider stats (total_rides++, total_spent+=)
7. Notify all participants
COMMIT
```

**Rollback Conditions:**
- Ride not found or wrong status
- Wallet capture fails for any rider
- Transaction creation fails

**Compensation Logic:**
- Partial success requires full rollback
- Log all capture attempts
- Create refund records for successful captures if rollback needed

---

#### 2.3.5 Cancel Ride Transaction
```
BEGIN TRANSACTION
1. Validate ride (status != COMPLETED)
2. Fetch all requests with status=CONFIRMED/PENDING
3. FOR EACH request:
   a. Calculate cancellation fee (if applicable per BR-34, BR-45)
   b. Release wallet hold (pending_balance -= held_amount)
   c. Apply cancel fee if driver canceled late (pending_balance -= fee)
   d. Update request (status=CANCELLED, cancellation details)
   e. Create HOLD_RELEASE transaction
4. Update shared_ride (status=CANCELLED, cancellation_reason)
5. Update driver stats if penalty applied
6. Notify all affected riders and driver
COMMIT
```

**Rollback Conditions:**
- Ride not found
- Wallet release fails
- Transaction creation fails

**Compensation Logic:**
- If notification fails after commit: Retry async
- Log all refund attempts

---

### 2.4 Isolation Levels

| Operation | Isolation Level | Reason |
|-----------|-----------------|--------|
| Create Ride | READ_COMMITTED | No concurrency conflict expected |
| Request to Join | READ_COMMITTED | Seat check happens at accept, not request |
| Accept Request | SERIALIZABLE | Critical: seat availability check-and-increment |
| Complete Ride | SERIALIZABLE | Critical: multiple wallet captures must be atomic |
| Cancel Ride | SERIALIZABLE | Multiple refunds must be atomic |
| Browse Rides | READ_COMMITTED | Read-only, eventual consistency acceptable |

---

## 3. RBAC Matrix

| Endpoint | Public | Rider | Driver | Admin |
|----------|--------|-------|--------|-------|
| POST /api/v1/rides | ❌ | ❌ | ✅ | ✅ |
| PUT /api/v1/rides/{id} | ❌ | ❌ | ✅ (own) | ✅ |
| DELETE /api/v1/rides/{id} | ❌ | ❌ | ✅ (own) | ✅ |
| GET /api/v1/rides/{rideId} | ❌ | ✅ (involved) | ✅ (own) | ✅ |
| POST /api/v1/rides/{rideId}/start | ❌ | ❌ | ✅ (own) | ✅ |
| POST /api/v1/rides/{rideId}/complete | ❌ | ❌ | ✅ (own) | ✅ |
| GET /api/v1/rides/available | ❌ | ✅ | ❌ | ✅ |
| POST /api/v1/rides/{rideId}/requests | ❌ | ✅ | ❌ | ✅ |
| POST /api/v1/ride-requests | ❌ | ✅ | ❌ | ✅ |
| GET /api/v1/ride-requests/available | ❌ | ❌ | ✅ | ✅ |
| POST /api/v1/ride-requests/{requestId}/accept | ❌ | ❌ | ✅ (own ride) | ✅ |
| POST /api/v1/ride-requests/{requestId}/deny | ❌ | ❌ | ✅ (own ride) | ✅ |
| DELETE /api/v1/ride-requests/{id} | ❌ | ✅ (own) | ❌ | ✅ |

### 3.1 Authorization Logic

**Driver Ownership Check:**
```java
boolean isDriverOwnRide(int userId, int rideId) {
    SharedRide ride = rideRepository.findById(rideId);
    return ride != null && ride.getDriver().getDriverId() == userId;
}
```

**Rider Involvement Check:**
```java
boolean isRiderInvolvedInRide(int userId, int rideId) {
    return requestRepository.existsByRiderIdAndSharedRideIdAndStatusIn(
        userId, rideId, List.of(PENDING, CONFIRMED, ONGOING, COMPLETED)
    );
}
```

**Profile Verification:**
- Driver must have `driver_profiles.status = 'ACTIVE'`
- Driver must have an active vehicle with valid insurance
- Rider must have `rider_profiles.status = 'ACTIVE'`
- User must have verified email and phone

---

## 4. State Machines

### 4.1 Shared Ride Status Machine

```
States:
- SCHEDULED: Ride created, waiting for requests/riders
- ACTIVE: (deprecated, use SCHEDULED) Ride has confirmed riders
- ONGOING: Ride has started
- COMPLETED: Ride finished successfully
- CANCELLED: Ride canceled by driver/admin

Transitions:
SCHEDULED → ONGOING   (driver starts ride)
SCHEDULED → CANCELLED (driver/admin cancels)
ONGOING → COMPLETED   (driver completes)
ONGOING → CANCELLED   (emergency cancellation)

Invalid Transitions:
- COMPLETED → * (terminal state)
- CANCELLED → * (terminal state)
- ONGOING → SCHEDULED
```

**State Transition Rules:**
1. **SCHEDULED → ONGOING:**
   - Must have at least 1 CONFIRMED request
   - Driver must initiate
   - Scheduled time should be close (within acceptable window)

2. **SCHEDULED → CANCELLED:**
   - No restriction on timing
   - Must provide reason if has confirmed riders
   - Apply cancellation penalties per BR-45

3. **ONGOING → COMPLETED:**
   - Driver must initiate
   - All riders must be dropped off (or marked as no-show)
   - Capture all payments atomically

4. **ONGOING → CANCELLED:**
   - Emergency only
   - Full refunds to all riders
   - Detailed investigation required

---

### 4.2 Shared Ride Request Status Machine

```
States:
- PENDING: Request created, awaiting driver response
- CONFIRMED: Driver accepted, rider confirmed
- ONGOING: Ride in progress
- COMPLETED: Ride completed successfully
- CANCELLED: Request canceled (rider, driver, or system)
- EXPIRED: Request timeout without driver acceptance

Transitions:
PENDING → CONFIRMED  (driver accepts)
PENDING → CANCELLED  (driver denies, rider cancels)
PENDING → EXPIRED    (timeout after T_ACCEPT seconds)
CONFIRMED → ONGOING  (ride starts)
CONFIRMED → CANCELLED (rider cancels before start, with fee)
ONGOING → COMPLETED  (ride completes)
ONGOING → CANCELLED  (emergency cancellation)

Invalid Transitions:
- COMPLETED → * (terminal)
- EXPIRED → * (terminal)
- CANCELLED → * (terminal)
```

**State Transition Rules:**
1. **PENDING → CONFIRMED:**
   - Driver must be owner of the ride
   - Seats must be available (atomic check)
   - Within T_ACCEPT window (default 30s per BR-26)
   - Wallet hold must succeed

2. **PENDING → EXPIRED:**
   - Automatic after T_ACCEPT timeout
   - Release wallet hold fully
   - Retry matching if AI_BOOKING

3. **CONFIRMED → ONGOING:**
   - Automatic when ride status → ONGOING
   - All confirmed requests transition together

4. **ONGOING → COMPLETED:**
   - Automatic when ride completes
   - Payment capture must succeed
   - Update stats atomically

5. **CONFIRMED → CANCELLED:**
   - Apply late cancellation fee per BR-32
   - Release remaining hold
   - Notify driver (seat becomes available)

---

### 4.3 Request Kind Flow

**AI_BOOKING Flow:**
```
1. Rider creates request (request_kind=AI_BOOKING, shared_ride_id=null, status=PENDING)
2. Matching algorithm runs
3. System offers to top driver (shared_ride_id still null)
4. Driver accepts → Update request (shared_ride_id=ride_id, status=CONFIRMED)
5. Driver declines/timeout → Offer to next driver (up to K=3 attempts)
6. No match → status=EXPIRED (or NO_MATCH)
```

**JOIN_RIDE Flow:**
```
1. Rider browses available rides
2. Rider creates request (request_kind=JOIN_RIDE, shared_ride_id=ride_id, status=PENDING)
3. Driver receives notification
4. Driver accepts → status=CONFIRMED
5. Driver declines → status=CANCELLED
6. Timeout → status=EXPIRED
```

---

## 5. Exception Mapping

### 5.1 New Error Definitions Required

Add to `errors.yaml`:

```yaml
# Ride Module Errors
- id: ride.not-found.by-id
  httpStatus: 404
  severity: INFO
  isRetryable: false
  messageTemplate: "Ride not found"
  domain: ride
  category: not-found
  owner: ride-team

- id: ride.validation.no-seats-available
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "No seats available for this ride"
  domain: ride
  category: validation
  owner: ride-team
  remediation: "Try another ride or wait for availability"

- id: ride.validation.invalid-status-transition
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Invalid ride status transition"
  domain: ride
  category: validation
  owner: ride-team

- id: ride.operation.cannot-modify-with-confirmed-riders
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Cannot modify ride with confirmed riders"
  domain: ride
  category: operation
  owner: ride-team
  remediation: "Cancel and create a new ride"

- id: ride.authorization.not-ride-owner
  httpStatus: 403
  severity: WARN
  isRetryable: false
  messageTemplate: "You are not the owner of this ride"
  domain: ride
  category: unauthorized
  owner: ride-team

- id: ride-request.not-found.by-id
  httpStatus: 404
  severity: INFO
  isRetryable: false
  messageTemplate: "Ride request not found"
  domain: ride-request
  category: not-found
  owner: ride-team

- id: ride-request.conflict.already-exists
  httpStatus: 409
  severity: WARN
  isRetryable: false
  messageTemplate: "You already have a pending or confirmed request for this ride"
  domain: ride-request
  category: conflict
  owner: ride-team
  remediation: "Cancel existing request before creating a new one"

- id: ride-request.validation.expired
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Ride request has expired"
  domain: ride-request
  category: validation
  owner: ride-team

- id: ride-request.operation.wallet-hold-failed
  httpStatus: 500
  severity: ERROR
  isRetryable: true
  messageTemplate: "Failed to place wallet hold for ride request"
  domain: ride-request
  category: operation
  owner: ride-team
  remediation: "Check wallet balance and try again"

- id: ride-request.authorization.not-request-participant
  httpStatus: 403
  severity: WARN
  isRetryable: false
  messageTemplate: "You are not authorized to access this ride request"
  domain: ride-request
  category: unauthorized
  owner: ride-team

- id: location.not-found.by-id
  httpStatus: 404
  severity: INFO
  isRetryable: false
  messageTemplate: "Location not found"
  domain: location
  category: not-found
  owner: location-team

- id: vehicle.not-found.by-id
  httpStatus: 404
  severity: INFO
  isRetryable: false
  messageTemplate: "Vehicle not found"
  domain: vehicle
  category: not-found
  owner: vehicle-team

- id: vehicle.validation.not-active
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Vehicle is not active"
  domain: vehicle
  category: validation
  owner: vehicle-team
  remediation: "Use an active vehicle"

- id: vehicle.validation.insurance-expired
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Vehicle insurance has expired"
  domain: vehicle
  category: validation
  owner: vehicle-team
  remediation: "Renew vehicle insurance before creating rides"

- id: driver.validation.not-active
  httpStatus: 403
  severity: WARN
  isRetryable: false
  messageTemplate: "Driver profile is not active"
  domain: driver
  category: validation
  owner: driver-team
  remediation: "Complete driver verification to activate your profile"

- id: rider.validation.not-active
  httpStatus: 403
  severity: WARN
  isRetryable: false
  messageTemplate: "Rider profile is not active"
  domain: rider
  category: validation
  owner: rider-team
  remediation: "Complete profile verification"
```

### 5.2 HTTP Status Code Mapping

| HTTP Status | Use Cases |
|-------------|-----------|
| 200 OK | Successful GET, PUT, POST (for actions) |
| 201 Created | Successful POST (create resource) |
| 400 Bad Request | Validation errors, business rule violations |
| 401 Unauthorized | Missing or invalid JWT token |
| 403 Forbidden | Insufficient permissions, inactive profile |
| 404 Not Found | Resource does not exist |
| 409 Conflict | Duplicate request, concurrent modification |
| 500 Internal Server Error | Unexpected errors, wallet/payment failures |

---

## 6. Flyway Migrations

### 6.1 V3__Update_shared_ride_requests_for_dual_flow.sql

```sql
-- Migration: Support AI_BOOKING and JOIN_RIDE flows
-- Date: 2025-10-04
-- Description: Add request_kind, make share_ride_id nullable, update statuses

BEGIN;

-- 1. Add request_kind column
ALTER TABLE shared_ride_requests
ADD COLUMN request_kind VARCHAR(20);

-- 2. Make shared_ride_id nullable
ALTER TABLE shared_ride_requests
ALTER COLUMN shared_ride_id DROP NOT NULL;

-- 3. Update existing requests to JOIN_RIDE (backfill)
UPDATE shared_ride_requests
SET request_kind = 'JOIN_RIDE'
WHERE request_kind IS NULL;

-- 4. Now make request_kind NOT NULL with default
ALTER TABLE shared_ride_requests
ALTER COLUMN request_kind SET NOT NULL;

ALTER TABLE shared_ride_requests
ALTER COLUMN request_kind SET DEFAULT 'JOIN_RIDE';

-- 5. Add constraint for request_kind values
ALTER TABLE shared_ride_requests
ADD CONSTRAINT chk_request_kind
CHECK (request_kind IN ('AI_BOOKING', 'JOIN_RIDE'));

-- 6. Update status enum to include EXPIRED
ALTER TABLE shared_ride_requests
DROP CONSTRAINT IF EXISTS chk_shared_ride_request_status;

ALTER TABLE shared_ride_requests
ADD CONSTRAINT chk_shared_ride_request_status
CHECK (status IN ('PENDING', 'CONFIRMED', 'ONGOING', 'COMPLETED', 'CANCELLED', 'EXPIRED'));

-- 7. Add tightened constraint for request_kind and shared_ride_id relationship
ALTER TABLE shared_ride_requests
DROP CONSTRAINT IF EXISTS chk_ai_booking_no_initial_ride;

ALTER TABLE shared_ride_requests
ADD CONSTRAINT chk_request_kind_ride_id_relationship
CHECK (
    -- AI_BOOKING: must start with null shared_ride_id when PENDING
    (request_kind = 'AI_BOOKING' AND status = 'PENDING' AND shared_ride_id IS NULL)
    OR
    -- Any confirmed/ongoing/completed request must have a shared_ride_id
    (status IN ('CONFIRMED', 'ONGOING', 'COMPLETED') AND shared_ride_id IS NOT NULL)
    OR
    -- JOIN_RIDE: must always have shared_ride_id (except terminal states)
    (request_kind = 'JOIN_RIDE' AND status NOT IN ('CANCELLED', 'EXPIRED') AND shared_ride_id IS NOT NULL)
    OR
    -- Terminal states can be null or not null (cancelled before assignment)
    (status IN ('CANCELLED', 'EXPIRED'))
);

-- 8. Add index for matching queries
CREATE INDEX idx_ride_requests_matching
ON shared_ride_requests(request_kind, status, pickup_time)
WHERE status = 'PENDING';

-- 9. Update shared_rides status to support SCHEDULED
ALTER TABLE shared_rides
DROP CONSTRAINT IF EXISTS chk_shared_rides_status;

ALTER TABLE shared_rides
ADD CONSTRAINT chk_shared_rides_status
CHECK (status IN ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED'));

-- 10. Update existing rides: PENDING/ACTIVE → SCHEDULED
UPDATE shared_rides
SET status = 'SCHEDULED'
WHERE status IN ('PENDING', 'ACTIVE');

COMMIT;
```

### 6.2 V4__Add_ride_module_indexes.sql

```sql
-- Migration: Add performance indexes for ride module
-- Date: 2025-10-04
-- Description: Indexes for browse, matching, and filtering operations

BEGIN;

-- 1. Composite index for browse available rides
CREATE INDEX idx_shared_rides_available_browse
ON shared_rides(status, scheduled_time, current_passengers, max_passengers)
WHERE status = 'SCHEDULED' AND current_passengers < max_passengers;

-- 2. Geospatial search support (for future optimization)
-- Note: Requires locations to have proper indexes, already exist in V1

-- 3. Driver's rides listing
CREATE INDEX idx_shared_rides_driver_status_time
ON shared_rides(driver_id, status, scheduled_time DESC);

-- 4. Rider's requests listing
CREATE INDEX idx_ride_requests_rider_status_time
ON shared_ride_requests(rider_id, status, created_at DESC);

-- 5. Driver's pending requests (for notification queries)
CREATE INDEX idx_ride_requests_ride_pending
ON shared_ride_requests(shared_ride_id, status)
WHERE status = 'PENDING';

-- 6. Concurrent seat check optimization
CREATE INDEX idx_shared_rides_id_passengers
ON shared_rides(shared_ride_id, current_passengers, max_passengers);

COMMIT;
```

### 6.3 V5__Add_driver_max_detour_config.sql

```sql
-- Migration: Add driver max detour preference
-- Date: 2025-10-04
-- Description: Support configurable detour limits for matching

BEGIN;

-- Add max_detour_minutes to driver_profiles
ALTER TABLE driver_profiles
ADD COLUMN max_detour_minutes INTEGER DEFAULT 8;

-- Add constraint
ALTER TABLE driver_profiles
ADD CONSTRAINT chk_max_detour_positive
CHECK (max_detour_minutes IS NULL OR max_detour_minutes > 0);

-- Add comment
COMMENT ON COLUMN driver_profiles.max_detour_minutes IS
'Maximum detour in minutes driver accepts for pickups (default 8, per BR)';

COMMIT;
```

---

## 7. Architecture Decisions

### 7.1 Matching Algorithm Placement

**Decision:** Implement matching as a separate service layer, not in REST controllers

**Rationale:**
- Complex business logic should not be in controllers
- Matching may involve external services (route, pricing)
- Testability and modularity
- Future: Can extract to separate microservice

**Implementation:**
```java
@Service
public class RideMatchingService {
    
    @Autowired private SharedRideRepository rideRepository;
    @Autowired private RoutingService routingService;
    @Autowired private PricingService pricingService;
    
    public List<MatchCandidate> findCandidateRides(
        MatchingRequest request, int maxCandidates) {
        // 1. Query candidate rides (geo + time window)
        // 2. Score each candidate
        // 3. Sort and return top N
    }
    
    public MatchResult attemptMatch(
        SharedRideRequest request, List<Integer> candidateRideIds) {
        // 1. Offer to top candidate
        // 2. Wait for driver response (async)
        // 3. On decline/timeout, try next
        // 4. Return match result or NO_MATCH
    }
}
```

---

### 7.2 Seat Availability Concurrency

**Decision:** Use pessimistic locking (SELECT FOR UPDATE) for accept request operation

**Rationale:**
- Optimistic locking (version field) causes frequent retry under load
- Seat availability is critical business constraint
- Small critical section (check + increment)
- Better user experience (immediate success/failure)

**Trade-offs:**
- Increased lock wait time under high concurrency
- Mitigated by: Short transaction, driver-specific lock scope

**Alternative Considered:** Redis distributed lock
- Rejected: Adds external dependency, complexity
- May revisit if database becomes bottleneck

---

### 7.3 Quote vs Dynamic Pricing

**Decision:** Use QuoteService for upfront pricing, recalculate on completion

**Rationale:**
- Riders need price certainty when requesting
- Actual distance may differ (detours, traffic)
- Quote provides hold amount
- Final fare adjusts for actuals

**Implementation:**
```java
// At request creation
Quote quote = quoteService.generateQuote(request, riderId);
request.setOriginalFare(quote.getTotalFare());
request.setFareAmount(quote.getFinalFare()); // after discounts

// At ride completion
FareBreakdown actual = pricingService.calculateFare(
    request.getActualDistance(), 
    ride.getPerKmRate(),
    request.getDiscountAmount()
);
// Capture actual.getFinalFare()
// Refund difference if actual < quoted
```

---

### 7.4 Notification Strategy

**Decision:** Async notifications with best-effort delivery

**Rationale:**
- Notification failures should not block ride operations
- Use Spring @Async or message queue
- Retry failed notifications separately

**Implementation:**
```java
@Async
public void notifyDriverNewRequest(int driverId, int requestId) {
    try {
        // Send push notification
        // Send SMS if push fails
        // Log notification event
    } catch (Exception e) {
        log.error("Failed to notify driver {}: {}", driverId, e.getMessage());
        // Queue for retry
    }
}
```

---

### 7.5 Request Timeout Handling

**Decision:** Database polling with scheduled job (not event-driven for MVP)

**Rationale:**
- Simpler implementation for MVP
- No need for distributed scheduler (Quartz, etc.)
- Acceptable latency (~1 minute)

**Configuration:**
```yaml
# application.yml
ride:
  request:
    accept-timeout-seconds: 30  # T_ACCEPT configurable per BR-26
    expiry-check-interval-ms: 60000  # How often to check for expired requests
    max-retry-attempts: 3  # K = 3 for AI_BOOKING matching retries
```

**Implementation:**
```java
@Scheduled(fixedDelayString = "${ride.request.expiry-check-interval-ms}")
public void expireTimedOutRequests() {
    int timeoutSeconds = rideConfig.getAcceptTimeoutSeconds(); // From config
    LocalDateTime cutoff = LocalDateTime.now().minus(timeoutSeconds, ChronoUnit.SECONDS);
    
    List<SharedRideRequest> timedOut = requestRepository
        .findByStatusAndCreatedAtBefore(PENDING, cutoff);
    
    for (SharedRideRequest request : timedOut) {
        String reason = String.format("Request expired after %d seconds without driver response", 
                                      timeoutSeconds);
        log.info("Expiring request {}: requestKind={}, riderId={}, reason={}", 
                 request.getId(), request.getRequestKind(), request.getRiderId(), reason);
        
        request.setStatus(EXPIRED);
        
        // Release wallet hold if JOIN_RIDE (AI_BOOKING has no hold yet)
        if (request.getRequestKind() == RequestKind.JOIN_RIDE) {
            walletService.releaseHold(request);
        }
        
        // Retry matching if AI_BOOKING and under retry limit
        if (request.getRequestKind() == RequestKind.AI_BOOKING) {
            matchingService.retryMatching(request);
        }
    }
    
    requestRepository.saveAll(timedOut);
}
```

**Future Enhancement:** Event-driven with message queue (SQS delay, Redis keyspace notifications)

---

### 7.6 DTO Validation Strategy

**Decision:** Use Jakarta Validation annotations + custom validators

**Rationale:**
- Declarative validation reduces boilerplate
- Framework integration (Spring Boot)
- Consistent error responses

**Example:**
```java
@Data
public class CreateSharedRideRequest {
    
    @NotNull(message = "Vehicle ID is required")
    @Positive
    private Integer vehicleId;
    
    @NotNull
    @Positive
    private Integer startLocationId;
    
    @NotNull
    @Positive
    private Integer endLocationId;
    
    @NotNull
    @Future(message = "Scheduled time must be in the future")
    private LocalDateTime scheduledTime;
    
    @Min(1) @Max(3)
    private Integer maxPassengers = 1;
    
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal baseFare;
    
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal perKmRate;
    
    // Custom validator
    @ValidScheduledTime(maxAdvanceHours = 24)
    private LocalDateTime scheduledTime;
}
```

---

### 7.7 Deviation from API Spec

**Documented Deviations:**

1. **Response Field: `driverEarnings` in Complete Ride**
   - API spec does not include this field
   - Added for transparency and driver UX
   - Rationale: Drivers want immediate earnings confirmation
   - Migration plan: Optional field, can be removed if frontend doesn't use

2. **Request Body: `actualDistance` in Complete Ride**
   - API spec does not include this optional field
   - Added to allow driver to override GPS distance if inaccurate
   - Rationale: GPS may be unreliable in some areas
   - Migration plan: Falls back to GPS distance if not provided

3. **Status `EXPIRED` for ride requests**
   - API spec lists: PENDING, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED
   - Added `EXPIRED` for timeout scenario
   - Rationale: Distinguish timeout from explicit cancellation
   - Migration plan: Can map to CANCELLED in API responses if needed

4. **Query parameter `requestKind` for browse requests**
   - Not in original spec
   - Added to filter by AI_BOOKING vs JOIN_RIDE
   - Rationale: Driver may want to see only AI-matched requests
   - Migration plan: Optional parameter, defaults to all kinds

All deviations documented in `/docs/other/RideImplementationLog.md`

---

### 7.8 MVP Scope Clarifications

#### 7.8.1 Promotion/Discount Flow (Deferred)

**Decision:** Accept promotion code as metadata only for MVP; defer validation/application

**Rationale:**
- Promotion system requires complex business rules (eligibility, usage limits, expiry)
- Quote/pricing services need promotion integration first
- Focus MVP on core ride flow

**MVP Implementation:**
```java
// Accept promotionCode in request DTO
public class CreateRideRequestDto {
    private String promotionCode; // Store but don't validate yet
}

// Store in shared_ride_request
request.setPromotionCode(dto.getPromotionCode());

// TODO: Integrate with PromotionService when available
// if (promotionCode != null) {
//     Promotion promo = promotionService.validate(promotionCode, userId, fareAmount);
//     discountAmount = promotionService.calculateDiscount(promo, fareAmount);
// }
```

**Extension Points:**
- `PromotionService` interface defined (empty implementation)
- Database columns ready (`promotion_code`, `discount_amount`, `original_fare`)
- DTO fields present
- Quote calculation supports discount parameter

**Post-MVP:** Implement full promotion validation, usage tracking, and discount application

---

#### 7.8.2 Notification Service (Placeholder)

**Decision:** Use placeholder notification service with logging for MVP

**Rationale:**
- Notification infrastructure (FCM, SMS gateway) requires external setup
- Business logic shouldn't block on notification failures
- MVP can function with email/in-app notifications only

**MVP Implementation:**
```java
@Service
public class NotificationService {
    
    // TODO: Integrate with FCM, Twilio, SendGrid
    public void notifyDriverNewRequest(int driverId, SharedRideRequest request) {
        log.info("NOTIFICATION: Driver {} has new request {}", driverId, request.getId());
        // Placeholder: Store in notifications table for in-app display
        notificationRepository.save(new Notification(
            driverId, "NEW_REQUEST", 
            "New ride request", 
            "You have a new ride request from rider " + request.getRiderId()
        ));
    }
    
    public void notifyRiderRequestAccepted(int riderId, SharedRideRequest request) {
        log.info("NOTIFICATION: Rider {} request accepted", riderId);
        notificationRepository.save(new Notification(
            riderId, "REQUEST_ACCEPTED",
            "Request accepted",
            "Your ride request has been accepted"
        ));
    }
}
```

**Extension Points:**
- `NotificationService` interface with all methods defined
- Async execution ready (`@Async`)
- Database table `notifications` populated
- Client can poll notifications table

**Post-MVP:** Integrate FCM for push, Twilio for SMS, SendGrid for email

---

#### 7.8.3 AI Matching Algorithm (Simplified MVP)

**Decision:** Implement basic proximity + time matching; defer advanced scoring

**Rationale:**
- Full corridor buffering and DTW/Frechet distance requires complex geometry
- MVP needs functional matching, not optimal matching
- Can iterate on algorithm without changing API contract

**MVP Implementation:**
```java
@Service
public class RideMatchingService {
    
    // TODO: Enhance with corridor buffering, route similarity, scoring weights
    public List<SharedRide> findCandidateRides(SharedRideRequest request) {
        // Simple proximity + time window matching
        double radiusKm = 5.0;
        LocalDateTime timeMin = request.getPickupTime().minusMinutes(10);
        LocalDateTime timeMax = request.getPickupTime().plusMinutes(20);
        
        return rideRepository.findCandidateRides(
            request.getPickupLocation().getLat(),
            request.getPickupLocation().getLng(),
            radiusKm,
            timeMin,
            timeMax,
            SharedRideStatus.SCHEDULED
        );
    }
    
    // TODO: Implement weighted scoring: distance + time + rating + detour
    public List<SharedRide> scoreAndRank(List<SharedRide> candidates, SharedRideRequest request) {
        // MVP: Simple distance sorting
        return candidates.stream()
            .sorted(Comparator.comparing(ride -> 
                calculateDistance(ride.getStartLocation(), request.getPickupLocation())))
            .limit(3) // K=3 candidates
            .collect(Collectors.toList());
    }
    
    // Haversine distance (simple, no route corridor)
    private double calculateDistance(Location a, Location b) {
        // Standard haversine formula
    }
}
```

**Extension Points for Post-MVP:**
- Route corridor buffering (pickup within X meters of polyline)
- DTW/Frechet distance for route similarity
- Multi-factor scoring (distance, time misalignment, detour, driver rating, load ratio)
- Configurable weights per scoring factor
- Machine learning model integration

**Documented in:** `// TODO: ENHANCEMENT:` comments throughout matching code

---

#### 7.8.4 Route Service Integration (Backend Validation)

**Decision:** Validate routes on backend using RoutingService; client is display-only

**Rationale:**
- Backend is source of truth for business logic
- Prevents client-side manipulation
- Centralized route caching and optimization
- Consistent fare calculation

**Implementation:**
```java
@Service
public class SharedRideService {
    
    @Autowired private RoutingService routingService;
    @Autowired private PricingService pricingService;
    
    public SharedRide createRide(CreateSharedRideDto dto, int driverId) {
        // Validate route exists and is reasonable
        Route route = routingService.calculateRoute(
            dto.getStartLocationId(),
            dto.getEndLocationId()
        );
        
        if (route == null || route.getDistance() > MAX_RIDE_DISTANCE_KM) {
            throw BaseDomainException.of("ride.validation.invalid-route");
        }
        
        // Use route data for estimates
        ride.setEstimatedDistance(route.getDistanceKm());
        ride.setEstimatedDuration(route.getDurationMinutes());
        
        // Calculate fare using routing data
        BigDecimal estimatedFare = pricingService.calculateFare(
            route.getDistanceKm(),
            dto.getPerKmRate(),
            BigDecimal.ZERO
        ).getFinalFare();
        
        ride.setBaseFare(estimatedFare);
        
        return rideRepository.save(ride);
    }
}
```

**Validation Points:**
- Ride creation: Validate route exists and distance reasonable
- Request to join: Validate pickup/dropoff reachable from ride route
- Accept request: Re-validate route with additional stop
- Complete ride: Validate actual distance within tolerance of estimate

**Post-MVP:** Add route optimization for multiple stops, real-time traffic integration

---

### 7.9 WebSocket Support for Real-Time Communication

**Decision:** Design architecture with WebSocket support in mind; implement placeholders for MVP

**Rationale:**
- Real-time features (driver location, ETA updates, instant notifications) require WebSocket
- MVP can function with HTTP polling; WebSocket is enhancement
- Architecture should not require major refactor to add WebSocket later

**Architectural Preparation:**
```java
// Controller structure ready for WebSocket upgrade
@RestController
@RequestMapping("/api/v1/rides")
public class SharedRideController {
    
    // HTTP endpoint (MVP)
    @GetMapping("/{rideId}/status")
    public RideStatusResponse getRideStatus(@PathVariable int rideId) {
        return rideService.getRideStatus(rideId);
    }
    
    // TODO: Add WebSocket endpoint post-MVP
    // @MessageMapping("/rides/{rideId}/subscribe")
    // @SendTo("/topic/rides/{rideId}")
    // public void subscribeToRideUpdates(@DestinationVariable int rideId) { }
}

// Service emits events (ready for WebSocket broadcast)
@Service
public class SharedRideService {
    
    // TODO: Inject WebSocket template post-MVP
    // @Autowired private SimpMessagingTemplate messagingTemplate;
    
    public void updateRideStatus(int rideId, SharedRideStatus newStatus) {
        ride.setStatus(newStatus);
        rideRepository.save(ride);
        
        // Event emission (currently no-op, will broadcast via WebSocket)
        publishRideStatusUpdate(rideId, newStatus);
    }
    
    private void publishRideStatusUpdate(int rideId, SharedRideStatus status) {
        // TODO: WebSocket broadcast
        // messagingTemplate.convertAndSend("/topic/rides/" + rideId, statusUpdate);
        log.info("Ride {} status updated to {}", rideId, status);
    }
}
```

**WebSocket Topics Planned:**
- `/topic/rides/{rideId}` - Ride status updates (SCHEDULED → ONGOING → COMPLETED)
- `/topic/drivers/{driverId}/requests` - New ride requests for driver
- `/topic/riders/{riderId}/updates` - Request acceptance, driver arrival, etc.
- `/topic/rides/{rideId}/location` - Driver real-time location (every 5 seconds)

**Configuration Stub:**
```java
// TODO: Enable post-MVP
// @Configuration
// @EnableWebSocketMessageBroker
// public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
//     @Override
//     public void configureMessageBroker(MessageBrokerRegistry registry) {
//         registry.enableSimpleBroker("/topic");
//         registry.setApplicationDestinationPrefixes("/app");
//     }
// }
```

**MVP Alternative:**
- Client polls `GET /api/v1/rides/{rideId}/status` every 5 seconds
- Client polls `GET /api/v1/notifications` for new notifications
- Push notifications via FCM for critical events (when notification service integrated)

**Post-MVP Implementation:**
1. Add Spring WebSocket dependency
2. Enable WebSocket configuration
3. Convert polling endpoints to WebSocket subscriptions
4. Add authentication for WebSocket connections (JWT in handshake)
5. Scale with Redis pub/sub for multi-instance deployments

---

## 8. Implementation Checklist

### 8.1 Pre-Implementation

- [ ] Review and approve this implementation plan
- [ ] Confirm API contract with frontend team
- [ ] Verify pricing service integration points
- [ ] Confirm wallet service transaction API
- [ ] Review security requirements with security team

### 8.2 Database Layer

- [ ] Run Flyway migrations V3, V4, V5
- [ ] Verify schema changes in dev/test environments
- [ ] Update entity classes (SharedRide, SharedRideRequest)
- [ ] Create repository interfaces with JPA method queries
- [ ] Write repository integration tests

### 8.3 Service Layer

- [ ] Implement SharedRideService (CRUD operations)
- [ ] Implement SharedRideRequestService (request lifecycle)
- [ ] Implement RideMatchingService (candidate selection)
- [ ] Integrate with QuoteService (fare calculation)
- [ ] Integrate with RoutingService (route validation)
- [ ] Integrate with WalletService (hold/capture/release)
- [ ] Implement notification logic (async)
- [ ] Write service unit tests (Mockito)

### 8.4 Controller Layer

- [ ] Implement SharedRideController (driver endpoints)
- [ ] Implement SharedRideRequestController (rider endpoints)
- [ ] Add OpenAPI annotations (@Operation, @ApiResponse)
- [ ] Add validation annotations on DTOs
- [ ] Add security annotations (@PreAuthorize)
- [ ] Write controller integration tests (MockMvc)

### 8.5 DTO & Mapper Layer

- [ ] Create request DTOs (validation annotations)
- [ ] Create response DTOs (matching API spec)
- [ ] Create MapStruct mappers (entity ↔ DTO)
- [ ] Handle null/optional fields correctly
- [ ] Write mapper unit tests

### 8.6 Exception Handling

- [ ] Add new error definitions to errors.yaml
- [ ] Create custom exceptions (if needed beyond BaseDomainException)
- [ ] Verify GlobalExceptionHandler mappings
- [ ] Test error response formats

### 8.7 Documentation

- [ ] Create RideFlow.md (user-facing flow documentation)
- [ ] Create RideImplementationLog.md (decision log)
- [ ] Update README.md with ride endpoints
- [ ] Generate Swagger/OpenAPI spec
- [ ] Create Postman collection for testing

### 8.8 Testing & Quality

- [ ] Unit tests (services, mappers, validators)
- [ ] Integration tests (repositories, controllers)
- [ ] End-to-end tests (full ride flow)
- [ ] Concurrency tests (seat availability)
- [ ] Performance tests (browse queries)
- [ ] Security tests (authorization)
- [ ] Load tests (concurrent requests)

### 8.9 Deployment

- [ ] Run linter and fix issues
- [ ] Code review
- [ ] Merge to main branch
- [ ] Deploy to staging environment
- [ ] Run smoke tests
- [ ] Monitor logs and metrics
- [ ] Deploy to production (after approval)

---

## 9. Risks & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| **Concurrent seat booking conflicts** | High | Medium | Pessimistic locking, SERIALIZABLE isolation, comprehensive tests |
| **Wallet hold/capture failures** | High | Low | Transactional boundaries, compensation logic, monitoring |
| **Matching algorithm performance** | Medium | Medium | Database indexes, candidate limit (K=3), caching |
| **Notification delivery failures** | Low | Medium | Async processing, retry mechanism, fallback channels |
| **GPS/route service downtime** | Medium | Low | Fallback to haversine distance, cached routes, graceful degradation |
| **Database migration failures** | High | Low | Test migrations in staging, rollback scripts, data backup |

---

## 10. Success Criteria

✅ **Functional:**
- All 13 endpoints implemented and tested
- Happy path flows work end-to-end (AI_BOOKING and JOIN_RIDE)
- Error scenarios handled gracefully
- API contracts match specification
- Wallet hold timing correct per flow type

✅ **Non-Functional:**
- Response time < 2s for read operations
- Response time < 4s for write operations with wallet
- No data corruption under concurrent load
- Seat availability constraint never violated

✅ **Quality:**
- Test coverage > 80%
- No critical linter errors
- All migrations reversible
- Documentation complete

✅ **Security:**
- All endpoints require authentication
- RBAC enforced correctly
- No sensitive data in logs
- SQL injection prevented

---

## 11. Next Steps After Approval

1. **Week 1: Database & Entities**
   - Run migrations
   - Update entities
   - Create repositories
   - Repository tests

2. **Week 2: Core Services**
   - SharedRideService
   - SharedRideRequestService
   - Wallet integration
   - Service tests

3. **Week 3: Controllers & DTOs**
   - Controller implementation
   - DTO creation
   - Mapper implementation
   - Integration tests

4. **Week 4: Advanced Features & Polish**
   - Matching algorithm
   - Notification system
   - End-to-end tests
   - Documentation

---

**Document Version:** 1.0  
**Status:** Awaiting Approval  
**Next Review:** After user approval  
**Estimated Implementation Time:** 4 weeks (1 FTE)


