# Mobile Ride E2E Demo Script

## 1. Shared Setup

1. **Backend + services**
   - Deploy the current `backend` app with migrations applied and schedulers (`RideMatchingCoordinator`, `RideLifecycleWorker`) enabled.
   - Pricing configs keep commission rate at **10 %** (see `pricing_config.commission_rate`) so driver net = **90 %** of fare (9 000 đ on a 10 000 đ trip). Update the config before recording wallet balances.
   - Notification broker reachable at `wss://<host>/ws` for STOMP destinations `/user/queue/ride-matching`, `/user/queue/ride-offers`, `/user/queue/notifications`.
2. **Mobile**
   - Install the Expo build of `mobile/Mobile_motobike`, log in on two physical or emulated devices (one for riders, one for drivers). Enable location permissions for Goong map widgets.
   - Keep phones unlocked and disable OS battery optimisations to ensure background WebSocket timers keep running (matching relies on the countdown in `DriverDecisionGateway`).
3. **Data refresh**
   - Seed route template or POIs for **“S2.02 Vinhomes Grand Park → FPT University HCMC”** with a reference fare of **10 000 đ** so both the quote API and shared ride listings use the same data.
   - Make sure `SharedRideRepository` has no stale `ONGOING` rides for the demo accounts before each recording.

### 1.1 Demo Accounts & Baseline Wallets

| Actor label | Email / Login | Role | Default location context | Wallet before demo (available/pending) | Used in flows |
|-------------|---------------|------|--------------------------|----------------------------------------|---------------|
| Rider John Doe | `john.doe@example.com` / `Password1!` | RIDER | Standing at **S2.02 Vinhomes Grand Park**, wants to commute toward **FPTU** | 300 000 đ / 0 đ | Flow 1 (join ride) |
| Driver Driver One | `driver1@example.com` / `Password1!` | DRIVER | Schedules a shared ride from **S2.02** to **FPTU** | 300 000 đ / 0 đ | Flow 1 |
| Rider John Doe | `john.doe@example.com` / `Password1!` | RIDER | Finishes a study session at **FPTU**, wants to return to **S2.02** | 290 000 đ / 0 đ | Flow 2 (Booking) |
| Driver Driver One | `driver1@example.com` / `Password1!` | DRIVER | Finishes a study session at **FPTU**, plans to return to **S2.02**, open a shared ride | 309 000 đ / 0 đ | Flow 2 |
| Rider John Doe | `john.doe@example.com` / `Password1!` | RIDER | Needs a quick hop back to **FPTU** for a project meeting | 280 000 đ / 0 đ | Flow 3 (broadcast) |
| Driver Driver One | `driver1@example.com` / `Password1!` | DRIVER | Finishes previous ride, want to grab a publishing request as freelancer gig | 318 000 đ / 0 đ | Flow 3 |

> **Wallet checkpoints**: Use the rider bottom tab `Ví` (`WalletScreen`) and driver tab `Ví tài xế` before each flow to capture the starting balance. During flows, wallet updates are triggered by `RideFundCoordinatingService` when ride requests are confirmed or completed; refresh the wallet screen via pull-to-refresh to reveal new holds/credits.

---

## 2. Flow 1 – Rider Joins an Existing Shared Ride

### 2.1 Scenario Snapshot

| Item | Details |
|------|---------|
| Time slot | 07:30 – morning shuttle |
| Driver | **Driver One** posts a scheduled shared ride from **S2.02** to **FPTU** with 1 empty seat. |
| Rider | **John Doe** is already in front of S2.02 lobby and prefers to hop on the scheduled ride instead of booking a fresh one. |
| Route & fare | Fixed at 10 000 đ (about 3.2 km). With 10 % commission, driver receives 9 000 đ net. |
| Connectivity | Driver app online (green websocket chip), rider app subscribed to `/queue/ride-matching`. |

### 2.2 Wallet Timeline (Flow 1)

| Stage | Rider John (available/pending) | Driver One (available/pending) | Notes |
|-------|-------------------------------|--------------------------------|-------|
| Before request | 300 000 đ / 0 đ | 300 000 đ / 0 đ | Snapshot both wallets. |
| After rider taps “Đặt xe ngay” in join mode | 290 000 đ / 10 000 đ | 300 000 đ / 0 đ | Hold placed by `SharedRideRequestService.joinRide`. |
| After ride completion | 290 000 đ / 0 đ | 309 000 đ / 0 đ | `RideFundCoordinatingServiceImpl` captures rider hold and credits driver net. |

### 2.3 Walkthrough

**Driver creates shared ride**
1. Log in on the driver device → `DriverHomeScreen`. Ensure the online toggle is on.
2. Tap **“Tạo chuyến chia sẻ”** and enter:
   - Route: Select route **S2.02 Vinhomes Grand Park to FPT University HCMC**
   - Departure: Depend on demo time.
3. Submit; toast confirms and ride enters the driver’s list as `SCHEDULED`.

**Rider discovers and joins**
4. On the rider device (`HomeScreen`), tap **“Tìm chuyến đi”** to open `AvailableRidesScreen`.
5. Locate Driver One’s card (S2.02 → FPTU). Tap **“Tham gia”**.
6. `RideBookingScreen` opens in `join_ride` mode with drop-off locked. Verify pickup pinned to John’s GPS and hit **“Xem giá cước”** → quote shows 10 000 đ.
7. Tap **“Đặt xe ngay”**. The app calls `rideService.joinRide` (POST `/api/v1/shared-rides/{rideId}/requests`). UI shows pending state and wallet hold is visible in the `Ví` tab.

**Driver accepts + tracking**
8. Driver receives a `RideOfferModal` notification with 90 s countdown. Tap **“Nhận chuyến đi”** (POST `/api/v1/ride-requests/{requestId}/accept`).
9. Both devices open their respective tracking screens. Driver taps **“Nhận khách”** once departing, then **“Đã đón khách”** once John is on board.

**Completion**
10. On arrival at FPTU, driver taps **“Hoàn thành chuyến đi”** → backend closes the ride and rider request, the wallet hold is captured (balances match the table).
11. Optional: demonstrate rating submission via `RatingScreen`.

### 2.4 Verification
- `GET /api/v1/ride-requests/{id}` returns status `COMPLETED`, fare 10 000 đ.
- `ride_transactions` table entry shows commission 1 000 đ, driver amount 9 000 đ.
- Rider wallet ledger shows `HOLD_CREATED` and `HOLD_CAPTURED` for 10 000 đ.

---

## 3. Flow 2 – Rider Books a system-matched Ride (System-Led Matching)

### 3.1 Scenario Snapshot

| Item | Details |
|------|---------|
| Time slot | 12:05 after classes |
| Rider | **John Doe** finishes at **FPTU** and wants to return to **S2.02** without waiting for a scheduled trip. |
| Driver | **Driver One** is idling at FPTU after finishing study session, ready to accept booking offers. |
| Expected fare | 10 000 đ; driver take-home 9 000 đ if matched. |
| Backend emphasis | Exercises `RideMatchingCoordinator` + `DriverDecisionGateway` sequential offer path. |

### 3.2 Wallet Timeline (Flow 2)

| Stage | Rider John | Driver One | Notes |
|-------|------------|------------|-------|
| Before booking | 290 000 đ / 0 đ | 309 000 đ / 0 đ | Reflects balances after Flow 1. |
| After rider taps “Đặt xe ngay” | 280 000 đ / 10 000 đ | 309 000 đ / 0 đ | Hold executed when the booking request is created. |
| After ride completion | 280 000 đ / 0 đ | 318 000 đ / 0 đ | Rider charged, driver credited 9 000 đ. |

### 3.3 Walkthrough

**Driver creates shared ride**
1. Log in on the driver device → `DriverHomeScreen`. Ensure the online toggle is on.
2. Tap **“Tạo chuyến chia sẻ”** and enter:
   - Route: Select route **S2.02 Vinhomes Grand Park to FPT University HCMC**
   - Departure: Depend on demo time.
3. Submit; toast confirms and ride enters the driver’s list as `SCHEDULED`.

**Ride matched**
1. On the rider device, tap **“Đặt xe ngay”** from `HomeScreen`.
2. In `RideBookingScreen`, route FPT University HCMC to S2.02 Vinhomes Grand Park. Tap **“Xem giá cước”** then **“Đặt xe ngay”**. The app calls `rideService.bookRide(quoteId)` and navigates to `RiderMatchingScreen`.
3. The matching animation starts. Mention that `RideMatchingCoordinator` queries matches, obtains Driver One, and sends an offer through `/user/<driverId>/queue/ride-offers`.
4. On the driver device, a `RideOfferModal` appears. Accept to call `rideService.acceptRideRequest` → backend confirms via `SharedRideRequestService.acceptRequest`.
5. Rider receives `ACCEPTED` notification and transitions to `RideTrackingScreen`. Both parties monitor the map until pickup.
6. Driver taps **“Nhận khách”** → `POST /api/v1/rides/start-ride-request`. After drop-off, tap **“Hoàn thành chuyến đi”** to capture the hold.
7. Demonstrate rider rating + receipt view if desired.

### 3.4 Validation Points
- WebSocket logs show sequential matching (offer sent, accepted) with no broadcast fallback.
- `shared_ride_requests` row shows `kind=BOOKING`, `status=COMPLETED`.
- Wallet history demonstrates the listed balances and ledger events.

---

## 4. Flow 3 – Driver Claims a Rider’s Broadcast Request

### 4.1 Scenario Snapshot

| Item | Details |
|------|---------|
| Time slot | 18:20 evening rush |
| Rider | **John Doe** needs to dash back to **FPTU** for a project meeting. All sequential candidates are currently busy, so the system will fall back to broadcast. |
| Driver | **Driver One** does not create shared ride to browse broadcast requests manually. |
| Route & fare | Still the 10 000 đ S2.02 ↔ FPTU hop. Driver expects another 9 000 đ net. |
| Backend emphasis | Demonstrates `RideMatchingCoordinator.switchToBroadcast` + `SharedRideRequestService.acceptBroadcast`. |

### 4.2 Wallet Timeline (Flow 3)

| Stage | Rider John | Driver One | Notes |
|-------|------------|------------|-------|
| Before broadcast | 280 000 đ / 0 đ | 318 000 đ / 0 đ | Carry-over from Flow 2. |
| While broadcast pending | 270 000 đ / 10 000 đ | 318 000 đ / 0 đ | Hold created. |
| After ride completion | 270 000 đ / 0 đ | 327 000 đ / 0 đ | Another 9 000 đ payout hits driver wallet. |

### 4.3 Walkthrough

**Trigger broadcast**
1. Rider repeats the booking steps (pickup S2.02, drop-off FPTU). Let the request sit so `RideMatchingCoordinator` exhausts sequential drivers.

**Driver claims request manually**
2. On the driver device, toggle offline (so no sequential offers arrive) and open **“Yêu cầu chờ”** → `AvailableRequestsScreen`.
3. Pull to refresh; John’s broadcast request appears with fare 10 000 đ. Tap it → confirm dialog → accept. This fires `rideService.acceptBroadcastRequest(requestId, vehicleId, currentLocation)`.
4. Backend endpoint `POST /api/v1/ride-requests/{requestId}/broadcast/accept` creates a new shared ride on the fly and confirms the rider.

**Tracking & completion**
5. Both apps jump to their tracking screens. Flow is identical to previous rides: driver taps **“Nhận khách”**, completes, and watches wallet update to 327 000 đ.

### 4.4 Observability
- Backend logs show ride coordinator entering `BROADCASTING` state for the request.
- `shared_ride_requests` record indicates `offer_type=BROADCAST`, `shared_ride_id` referencing the newly created trip.
- Wallet ledger entries show `HOLD_CREATED`, `HOLD_CAPTURED`.

---

## 5. Reset & Troubleshooting Tips

- **Reset data**: If something stalls (e.g., rider stuck in `ONGOING`), call `POST /api/v1/ride-requests/{id}/cancel` with the rider token or mark rides `COMPLETED` through admin APIs before restarting.
- **Wallet sync**: Wallet screens cache data for ~30 s. Always pull-to-refresh after each ride so the balances match the tables above.
- **WebSocket drift**: If either device stops receiving live events, reopen the app to re-run `websocketService.connectAsRider/Driver` before continuing.
- **GPS simulation**: On emulators, use the “Giả lập tới điểm đón/điểm đến” controls inside `DriverRideTrackingScreen` to animate movement instead of physically riding.
