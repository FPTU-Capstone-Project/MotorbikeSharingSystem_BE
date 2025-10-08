# 🚀 Motorbike Sharing System - Sprint Planning for Product Delivery

## 📊 Current Status Overview

### ✅ Completed Modules (5/17 - 29%)
1. **Authentication & Account Management** - Fully functional
2. **User Profiles** (Rider, Driver, Admin) - Complete with role switching
3. **Verification System** (Student & Driver KYC) - Comprehensive workflow
4. **Vehicle Management** - Full CRUD with filtering
5. **File Upload System** - Supporting document uploads
6. **Email Service** - Notification infrastructure

### ⚠️ Partial Implementation (3/17 - 18%)
1. **Wallet & Payment System** - Core complete, missing transaction history endpoint
2. **Reports & Analytics** - Controllers exist but no service implementations
3. **Admin Wallet Management** - Endpoints defined but not implemented

### ❌ Not Started (9/17 - 53%)
1. **Booking/Ride Request System** 🔴 **CRITICAL**
2. **Ride Matching System** 🔴 **CRITICAL**
3. **Location/GPS Tracking** 🟠 **HIGH**
4. **Emergency/SOS System** 🟠 **HIGH**
5. **Rating & Review System** 🟡 **MEDIUM**
6. **Notification System** 🟡 **MEDIUM**
7. **Messaging/Chat System** 🟡 **MEDIUM**
8. **Promotion/Discount System** 🟢 **LOW**
9. **Emergency Contact Management** 🟢 **LOW**

---

## 🎯 Sprint Strategy

### Delivery Timeline: **6 Sprints (12 weeks)**
- **Sprint Duration:** 2 weeks each
- **Team Velocity:** Assumes 2-3 features per sprint
- **Goal:** Deliver MVP with core ride-sharing functionality

### Sprint Goals Distribution:
- **Sprints 1-2:** Core Ride Functionality (CRITICAL)
- **Sprints 3-4:** Safety & User Experience (HIGH)
- **Sprints 5-6:** Enhancement & Polish (MEDIUM/LOW)

---

# 🏃 SPRINT 1: Core Ride Booking Foundation
**Duration:** Week 1-2
**Goal:** Enable basic ride request and booking flow

## 📋 User Stories

### US-1.1: Rider Can Request a Ride
**Priority:** 🔴 CRITICAL
**Story Points:** 8

**Acceptance Criteria:**
- Rider can create ride request with pickup/dropoff locations
- System validates locations and calculates estimated distance
- Ride request stored in database with PENDING status
- Rider receives confirmation

**Technical Tasks:**
- [ ] Create `SharedRideRequestRepository.java`
- [ ] Create `RideRequestService.java` interface
- [ ] Implement `RideRequestServiceImpl.java`
- [ ] Create `RideRequestController.java` with endpoints:
  - `POST /api/v1/rides/requests` - Create ride request
  - `GET /api/v1/rides/requests` - Get user's ride requests
  - `GET /api/v1/rides/requests/{id}` - Get ride request details
  - `PUT /api/v1/rides/requests/{id}/cancel` - Cancel ride request
- [ ] Create DTOs: `CreateRideRequestDTO`, `RideRequestResponseDTO`
- [ ] Add validation for locations (lat/lng ranges)
- [ ] Write unit tests for service layer
- [ ] Write integration tests for API endpoints

**Estimated Hours:** 24h

---

### US-1.2: Driver Can Create Shared Ride Offer
**Priority:** 🔴 CRITICAL
**Story Points:** 8

**Acceptance Criteria:**
- Driver can publish available ride with route and schedule
- System validates driver is ACTIVE and verified
- System validates vehicle is available
- Ride offer visible to matching riders

**Technical Tasks:**
- [ ] Create `SharedRideRepository.java`
- [ ] Create `SharedRideService.java` interface
- [ ] Implement `SharedRideServiceImpl.java`
- [ ] Create `SharedRideController.java` with endpoints:
  - `POST /api/v1/rides` - Create shared ride offer
  - `GET /api/v1/rides` - Get all available rides
  - `GET /api/v1/rides/{id}` - Get ride details
  - `PUT /api/v1/rides/{id}/cancel` - Cancel ride offer
- [ ] Create DTOs: `CreateSharedRideDTO`, `SharedRideResponseDTO`
- [ ] Add validation for driver status and vehicle availability
- [ ] Implement ride availability logic
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

### US-1.3: Complete Wallet Transaction History
**Priority:** 🔴 CRITICAL
**Story Points:** 3

**Acceptance Criteria:**
- Users can view their transaction history with pagination
- Transactions show type, amount, status, timestamp
- Can filter by transaction type and date range

**Technical Tasks:**
- [ ] Implement `WalletController.getTransactions()` method (currently throws UnsupportedOperationException)
- [ ] Add filtering logic in `TransactionService`
- [ ] Create `TransactionFilterDTO` for query parameters
- [ ] Add pagination support
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 8h

---

## Sprint 1 Definition of Done:
- [ ] All code reviewed and merged to main
- [ ] Unit test coverage > 80%
- [ ] Integration tests passing
- [ ] API documentation updated in Swagger
- [ ] Manual testing completed
- [ ] No critical bugs

**Total Story Points:** 19
**Total Estimated Hours:** 56h

---

# 🏃 SPRINT 2: Ride Matching & Booking Completion
**Duration:** Week 3-4
**Goal:** Enable automatic ride matching and complete booking flow

## 📋 User Stories

### US-2.1: Automatic Ride Matching Algorithm
**Priority:** 🔴 CRITICAL
**Story Points:** 13

**Acceptance Criteria:**
- System automatically matches ride requests with available shared rides
- Matching considers: distance, route overlap, time, capacity
- Match score calculated and ranked
- Top 5 matches shown to rider
- Match results logged for AI improvement

**Technical Tasks:**
- [ ] Create `AiMatchingLogRepository.java`
- [ ] Create `MatchingService.java` interface
- [ ] Implement `MatchingServiceImpl.java` with matching algorithm:
  - Calculate route overlap using lat/lng
  - Calculate distance deviation
  - Score matches based on multiple factors
  - Rank results
- [ ] Create `MatchingController.java` with endpoints:
  - `POST /api/v1/matching/find-rides` - Find matching rides for request
  - `GET /api/v1/matching/ride/{rideId}/requests` - Find matching requests for ride
- [ ] Create DTOs: `MatchResultDTO`, `MatchScoreDTO`
- [ ] Implement matching algorithm v1 (distance-based)
- [ ] Log all match attempts to `ai_matching_log`
- [ ] Write comprehensive unit tests for matching logic
- [ ] Write integration tests

**Estimated Hours:** 40h

---

### US-2.2: Driver Can Accept Ride Request
**Priority:** 🔴 CRITICAL
**Story Points:** 5

**Acceptance Criteria:**
- Driver can view pending ride requests for their ride offer
- Driver can accept a request
- System validates capacity and prevents double-booking
- Rider receives notification of acceptance
- Ride status updates to CONFIRMED

**Technical Tasks:**
- [ ] Add endpoint `POST /api/v1/rides/{rideId}/accept-request/{requestId}` to `SharedRideController`
- [ ] Implement acceptance logic in `SharedRideService`:
  - Validate driver owns the ride
  - Check available capacity
  - Update ride request status to CONFIRMED
  - Update shared ride capacity
  - Send notification to rider (email for now)
- [ ] Add validation for capacity overflow
- [ ] Create `AcceptRequestDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 16h

---

### US-2.3: Complete Ride Lifecycle
**Priority:** 🔴 CRITICAL
**Story Points:** 8

**Acceptance Criteria:**
- Driver can start ride
- Driver can mark ride as completed
- Rider can confirm ride completion
- System processes payment automatically
- Wallet balance updated for both parties

**Technical Tasks:**
- [ ] Add endpoints to `SharedRideController`:
  - `PUT /api/v1/rides/{id}/start` - Start ride
  - `PUT /api/v1/rides/{id}/complete` - Complete ride
  - `PUT /api/v1/rides/{id}/confirm-completion` - Rider confirms
- [ ] Implement ride lifecycle in `SharedRideService`:
  - Validate status transitions
  - Start ride: Set status to IN_PROGRESS, record start time
  - Complete ride: Set status to COMPLETED, record end time, trigger payment
  - Confirm completion: Both parties confirm, release wallet funds
- [ ] Integrate with `BookingWalletService`:
  - Hold funds when ride starts
  - Capture payment when completed
  - Calculate commission for platform
- [ ] Add status transition validation
- [ ] Write unit tests for each status transition
- [ ] Write integration tests for full ride lifecycle

**Estimated Hours:** 24h

---

## Sprint 2 Definition of Done:
- [ ] Matching algorithm working with test data
- [ ] End-to-end ride flow functional (request → match → accept → start → complete → payment)
- [ ] All code reviewed and merged
- [ ] Unit test coverage > 80%
- [ ] Integration tests passing
- [ ] API documentation updated
- [ ] Manual testing completed
- [ ] No critical bugs

**Total Story Points:** 26
**Total Estimated Hours:** 80h

---

# 🏃 SPRINT 3: Location Tracking & Real-Time Updates
**Duration:** Week 5-6
**Goal:** Enable real-time location tracking and ride progress monitoring

## 📋 User Stories

### US-3.1: Real-Time Location Tracking
**Priority:** 🟠 HIGH
**Story Points:** 13

**Acceptance Criteria:**
- Driver app sends location updates every 10 seconds during ride
- Rider can view driver's real-time location on map
- System stores location history for completed rides
- Location data used for route verification

**Technical Tasks:**
- [ ] Create `LocationRepository.java`
- [ ] Create `LocationService.java` interface
- [ ] Implement `LocationServiceImpl.java`
- [ ] Create `LocationController.java` with endpoints:
  - `POST /api/v1/location/update` - Update current location (driver)
  - `GET /api/v1/location/driver/{driverId}/current` - Get driver's current location
  - `GET /api/v1/location/ride/{rideId}/track` - Get ride location history
- [ ] Implement location caching (Redis recommended)
- [ ] Add location update throttling (max 1 update per 5 seconds)
- [ ] Store location snapshots every 30 seconds for history
- [ ] Calculate ETA based on current location
- [ ] Create DTOs: `LocationUpdateDTO`, `LocationResponseDTO`
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Performance test for high-frequency updates

**Estimated Hours:** 40h

---

### US-3.2: WebSocket for Real-Time Updates
**Priority:** 🟠 HIGH
**Story Points:** 8

**Acceptance Criteria:**
- WebSocket connection established for riders and drivers
- Real-time updates for ride status changes
- Real-time location updates pushed to rider
- Graceful reconnection handling

**Technical Tasks:**
- [ ] Add WebSocket dependencies to `pom.xml` (Spring WebSocket, STOMP)
- [ ] Create `WebSocketConfig.java` for configuration
- [ ] Create `WebSocketController.java` for message handling
- [ ] Implement WebSocket endpoints:
  - `/ws` - WebSocket connection endpoint
  - `/topic/ride/{rideId}/location` - Location updates
  - `/topic/ride/{rideId}/status` - Status updates
  - `/user/queue/notifications` - Personal notifications
- [ ] Integrate WebSocket with `LocationService` and `SharedRideService`
- [ ] Add authentication for WebSocket connections (JWT)
- [ ] Implement connection management and cleanup
- [ ] Write integration tests for WebSocket
- [ ] Document WebSocket protocol for mobile team

**Estimated Hours:** 24h

---

### US-3.3: Ride Progress Monitoring
**Priority:** 🟠 HIGH
**Story Points:** 5

**Acceptance Criteria:**
- Rider can see ride progress percentage
- ETA updates in real-time based on location
- Alerts if driver deviates significantly from route

**Technical Tasks:**
- [ ] Add method to `LocationService` to calculate ride progress
- [ ] Implement route deviation detection algorithm
- [ ] Add endpoint `GET /api/v1/rides/{id}/progress` to get ride progress
- [ ] Calculate progress based on distance covered vs total distance
- [ ] Update ETA calculation using current speed and remaining distance
- [ ] Send alert if deviation > 1km from planned route
- [ ] Create `RideProgressDTO`
- [ ] Write unit tests for progress calculation
- [ ] Write unit tests for deviation detection

**Estimated Hours:** 16h

---

## Sprint 3 Definition of Done:
- [ ] Location updates working in real-time
- [ ] WebSocket connections stable and tested
- [ ] Ride progress visible to users
- [ ] All code reviewed and merged
- [ ] Unit test coverage > 80%
- [ ] Integration tests passing
- [ ] Performance testing completed
- [ ] API documentation updated
- [ ] WebSocket protocol documented
- [ ] No critical bugs

**Total Story Points:** 26
**Total Estimated Hours:** 80h

---

# 🏃 SPRINT 4: Safety & Emergency Features
**Duration:** Week 7-8
**Goal:** Implement critical safety features for university students

## 📋 User Stories

### US-4.1: Emergency SOS System
**Priority:** 🟠 HIGH
**Story Points:** 13

**Acceptance Criteria:**
- Rider/Driver can trigger SOS alert with one tap
- SOS alert includes current location, ride details, and alert type
- Admin dashboard shows all active SOS alerts
- Emergency contacts notified via SMS/email
- SOS alerts logged and trackable

**Technical Tasks:**
- [ ] Create `SosAlertRepository.java`
- [ ] Create `SosAlertService.java` interface
- [ ] Implement `SosAlertServiceImpl.java`
- [ ] Create `SosAlertController.java` with endpoints:
  - `POST /api/v1/sos/alert` - Trigger SOS alert
  - `GET /api/v1/sos/alerts` - Get all alerts (admin)
  - `GET /api/v1/sos/alerts/{id}` - Get alert details
  - `PUT /api/v1/sos/alerts/{id}/acknowledge` - Admin acknowledges alert
  - `PUT /api/v1/sos/alerts/{id}/resolve` - Mark alert as resolved
- [ ] Integrate with `LocationService` to capture current location
- [ ] Integrate with `EmailService` for emergency notifications
- [ ] Add SMS notification (if SMS service available)
- [ ] Push SOS alerts to admin WebSocket channel
- [ ] Create DTOs: `CreateSosAlertDTO`, `SosAlertResponseDTO`
- [ ] Add alert type enum validation
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Create admin dashboard UI specs for mobile/web team

**Estimated Hours:** 40h

---

### US-4.2: Emergency Contact Management
**Priority:** 🟠 HIGH
**Story Points:** 5

**Acceptance Criteria:**
- Users can add up to 3 emergency contacts
- Emergency contacts receive SMS/email during SOS
- Users can update/delete emergency contacts
- Contacts validated for proper phone/email format

**Technical Tasks:**
- [ ] Create `EmergencyContactRepository.java`
- [ ] Create `EmergencyContactService.java` interface
- [ ] Implement `EmergencyContactServiceImpl.java`
- [ ] Create `EmergencyContactController.java` with endpoints:
  - `POST /api/v1/emergency-contacts` - Add emergency contact
  - `GET /api/v1/emergency-contacts` - Get user's emergency contacts
  - `PUT /api/v1/emergency-contacts/{id}` - Update emergency contact
  - `DELETE /api/v1/emergency-contacts/{id}` - Delete emergency contact
- [ ] Add validation: max 3 contacts per user
- [ ] Validate phone number format (Vietnamese)
- [ ] Integrate with `SosAlertService` for emergency notifications
- [ ] Create DTOs: `EmergencyContactDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 16h

---

### US-4.3: Ride Safety Features
**Priority:** 🟠 HIGH
**Story Points:** 5

**Acceptance Criteria:**
- Share ride details with friends/family (share link)
- Route deviation alerts
- Late arrival alerts (ETA exceeded by 15+ minutes)
- Night-time safety reminders

**Technical Tasks:**
- [ ] Add endpoint `POST /api/v1/rides/{id}/share` to generate shareable link
- [ ] Create public endpoint `GET /api/v1/rides/public/{shareToken}` to view ride tracking (no auth required)
- [ ] Implement share token generation and expiration (24 hours)
- [ ] Add route deviation detection in `LocationService`
- [ ] Add late arrival detection in `SharedRideService`
- [ ] Send notifications for safety alerts
- [ ] Create `RideShareDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 16h

---

## Sprint 4 Definition of Done:
- [ ] SOS system fully functional and tested
- [ ] Emergency contacts working with notifications
- [ ] Safety features activated and alerting correctly
- [ ] All code reviewed and merged
- [ ] Unit test coverage > 80%
- [ ] Integration tests passing
- [ ] Security audit completed for SOS features
- [ ] API documentation updated
- [ ] Manual testing with emergency scenarios
- [ ] No critical bugs

**Total Story Points:** 23
**Total Estimated Hours:** 72h

---

# 🏃 SPRINT 5: User Engagement & Social Features
**Duration:** Week 9-10
**Goal:** Enhance user experience with ratings, notifications, and messaging

## 📋 User Stories

### US-5.1: Rating & Review System
**Priority:** 🟡 MEDIUM
**Story Points:** 8

**Acceptance Criteria:**
- Users can rate completed rides (rider rates driver, driver rates rider)
- Rating includes overall score + subscores (safety, punctuality, communication)
- Users can leave text comments
- Average ratings displayed on profiles
- Cannot rate same ride twice

**Technical Tasks:**
- [ ] Create `RatingRepository.java`
- [ ] Create `RatingService.java` interface
- [ ] Implement `RatingServiceImpl.java`
- [ ] Create `RatingController.java` with endpoints:
  - `POST /api/v1/ratings` - Submit rating for completed ride
  - `GET /api/v1/ratings/ride/{rideId}` - Get ratings for specific ride
  - `GET /api/v1/ratings/driver/{driverId}` - Get driver's ratings
  - `GET /api/v1/ratings/rider/{riderId}` - Get rider's ratings
- [ ] Add validation: ride must be COMPLETED
- [ ] Add validation: user must be participant in ride
- [ ] Add validation: cannot rate twice
- [ ] Calculate and cache average ratings in user profiles
- [ ] Create DTOs: `CreateRatingDTO`, `RatingResponseDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

### US-5.2: Push Notification System
**Priority:** 🟡 MEDIUM
**Story Points:** 8

**Acceptance Criteria:**
- System sends notifications for key events (ride accepted, ride started, payment completed, etc.)
- Users receive in-app notifications
- Notifications marked as read/unread
- Notification preferences configurable
- Push notifications to mobile apps (FCM integration)

**Technical Tasks:**
- [ ] Create `NotificationRepository.java`
- [ ] Create `NotificationService.java` interface
- [ ] Implement `NotificationServiceImpl.java`
- [ ] Create `NotificationController.java` with endpoints:
  - `GET /api/v1/notifications` - Get user notifications (paginated)
  - `PUT /api/v1/notifications/{id}/read` - Mark notification as read
  - `PUT /api/v1/notifications/read-all` - Mark all as read
  - `DELETE /api/v1/notifications/{id}` - Delete notification
- [ ] Integrate notification triggers throughout app:
  - Ride request created
  - Ride request accepted
  - Ride started
  - Ride completed
  - Payment processed
  - SOS alert triggered
- [ ] Add Firebase Cloud Messaging (FCM) integration for mobile push
- [ ] Push notifications via WebSocket for in-app display
- [ ] Create DTOs: `NotificationResponseDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

### US-5.3: In-App Messaging System
**Priority:** 🟡 MEDIUM
**Story Points:** 8

**Acceptance Criteria:**
- Rider and driver can chat during ride
- Message history preserved
- Unread message count shown
- Messages delivered in real-time via WebSocket
- Message notifications

**Technical Tasks:**
- [ ] Create `MessageRepository.java`
- [ ] Create `MessageService.java` interface
- [ ] Implement `MessageServiceImpl.java`
- [ ] Create `MessageController.java` with endpoints:
  - `POST /api/v1/messages` - Send message
  - `GET /api/v1/messages/conversation/{rideId}` - Get conversation for ride
  - `GET /api/v1/messages/conversations` - Get all user's conversations
  - `PUT /api/v1/messages/{id}/read` - Mark message as read
  - `GET /api/v1/messages/unread-count` - Get unread message count
- [ ] Implement WebSocket for real-time message delivery:
  - `/topic/ride/{rideId}/messages` - Ride-specific chat channel
- [ ] Add message type validation (text, image, location)
- [ ] Integrate with `NotificationService` for message notifications
- [ ] Create DTOs: `SendMessageDTO`, `MessageResponseDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

## Sprint 5 Definition of Done:
- [ ] Rating system functional with average rating calculation
- [ ] Notifications sent for all key events
- [ ] In-app messaging working in real-time
- [ ] All code reviewed and merged
- [ ] Unit test coverage > 80%
- [ ] Integration tests passing
- [ ] API documentation updated
- [ ] Manual testing completed
- [ ] No critical bugs

**Total Story Points:** 24
**Total Estimated Hours:** 72h

---

# 🏃 SPRINT 6: Business Features & Final Polish
**Duration:** Week 11-12
**Goal:** Complete business features, admin tools, and prepare for production

## 📋 User Stories

### US-6.1: Promotion & Discount System
**Priority:** 🟢 LOW
**Story Points:** 8

**Acceptance Criteria:**
- Admin can create promotions with codes, discount types, validity periods
- Users can apply promo codes to rides
- System validates promo code eligibility (first-time user, student-only, etc.)
- Usage limits enforced (per user, total usage)
- Discount applied to ride cost

**Technical Tasks:**
- [ ] Create `PromotionRepository.java`
- [ ] Create `UserPromotionRepository.java`
- [ ] Create `PromotionService.java` interface
- [ ] Implement `PromotionServiceImpl.java`
- [ ] Create `PromotionController.java` with endpoints:
  - `POST /api/v1/admin/promotions` - Create promotion (admin)
  - `GET /api/v1/promotions/available` - Get available promotions
  - `POST /api/v1/promotions/validate` - Validate promo code
  - `POST /api/v1/promotions/apply` - Apply promotion to ride
  - `GET /api/v1/promotions/my` - Get user's promotions
- [ ] Implement validation logic:
  - Check validity dates
  - Check usage limits
  - Check target user type (student, new user, etc.)
- [ ] Integrate with ride pricing calculation
- [ ] Track promotion usage in `user_promotions` table
- [ ] Create DTOs: `CreatePromotionDTO`, `PromotionResponseDTO`, `ApplyPromotionDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

### US-6.2: Reports & Analytics Implementation
**Priority:** 🟢 MEDIUM
**Story Points:** 8

**Acceptance Criteria:**
- Admin can view wallet dashboard with key metrics
- Top-up trends report with charts
- Commission report for platform revenue
- Wallet reconciliation for financial audit
- All reports exportable as CSV

**Technical Tasks:**
- [ ] Create `ReportService.java` interface
- [ ] Implement `ReportServiceImpl.java`
- [ ] Implement all methods in `ReportController.java`:
  - `GET /api/v1/reports/wallet/dashboard` - Wallet statistics
  - `GET /api/v1/reports/wallet/topup-trends` - Top-up analysis
  - `GET /api/v1/reports/wallet/commission` - Commission report
- [ ] Implement queries for:
  - Total wallet balance across all users
  - Total transactions (count and volume)
  - Top-up trends by day/week/month
  - Commission earned by platform
  - Transaction success/failure rates
- [ ] Add date range filtering
- [ ] Add CSV export functionality
- [ ] Create DTOs: `WalletDashboardDTO`, `TopupTrendDTO`, `CommissionReportDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

### US-6.3: Admin Wallet Management Implementation
**Priority:** 🟢 MEDIUM
**Story Points:** 8

**Acceptance Criteria:**
- Admin can search/filter user wallets
- Admin can manually adjust wallet balance with reason
- Admin can distribute promotional credits in bulk
- Admin can freeze/unfreeze wallets
- All admin actions logged for audit

**Technical Tasks:**
- [ ] Create `AdminWalletService.java` interface
- [ ] Implement `AdminWalletServiceImpl.java`
- [ ] Implement all methods in `AdminWalletController.java`:
  - `GET /api/v1/admin/wallet/search` - Search wallets with filters
  - `POST /api/v1/admin/wallet/adjustment` - Manual balance adjustment
  - `POST /api/v1/admin/wallet/promo` - Distribute promotional credits
  - `GET /api/v1/admin/wallet/reconciliation` - Balance reconciliation
  - `POST /api/v1/admin/wallet/{userId}/freeze` - Freeze wallet
  - `POST /api/v1/admin/wallet/{userId}/unfreeze` - Unfreeze wallet
- [ ] Add audit logging for all admin wallet operations
- [ ] Add validation: adjustment reason required
- [ ] Add validation: promotion distribution amount limits
- [ ] Integrate with `TransactionService` to record all changes
- [ ] Create DTOs: `WalletAdjustmentDTO`, `PromoDistributionDTO`
- [ ] Write unit tests
- [ ] Write integration tests

**Estimated Hours:** 24h

---

### US-6.4: Production Readiness
**Priority:** 🔴 CRITICAL
**Story Points:** 5

**Acceptance Criteria:**
- All TODO comments resolved or converted to backlog items
- Error handling comprehensive and consistent
- Logging configured for production
- Database indices optimized
- Security audit completed
- API rate limiting configured
- Health check endpoints working
- Deployment documentation complete

**Technical Tasks:**
- [ ] Review and resolve all TODO comments in code
- [ ] Add comprehensive error handling to all controllers
- [ ] Configure production logging levels
- [ ] Review and optimize database queries
- [ ] Add database indices for frequently queried columns:
  - `shared_rides.status`, `shared_rides.scheduled_time`
  - `shared_ride_requests.status`, `shared_ride_requests.requested_time`
  - `transactions.user_id`, `transactions.created_at`
  - `locations.ride_id`, `locations.created_at`
- [ ] Security audit:
  - Review all authentication/authorization
  - Check for SQL injection vulnerabilities
  - Validate all user inputs
  - Review sensitive data encryption
- [ ] Configure API rate limiting (Spring Boot Bucket4j)
- [ ] Test health check endpoints (`/actuator/health`)
- [ ] Create deployment documentation:
  - Environment variables
  - Database migrations
  - Deployment steps
- [ ] Load testing for critical endpoints
- [ ] Create production runbook for common issues

**Estimated Hours:** 16h

---

## Sprint 6 Definition of Done:
- [ ] Promotion system functional
- [ ] All report endpoints implemented and tested
- [ ] Admin wallet management complete
- [ ] All TODO comments resolved
- [ ] Production checklist complete
- [ ] All code reviewed and merged
- [ ] Unit test coverage > 80%
- [ ] Integration tests passing
- [ ] Security audit completed
- [ ] Load testing completed
- [ ] Deployment documentation ready
- [ ] No critical bugs
- [ ] **READY FOR PRODUCTION DEPLOYMENT**

**Total Story Points:** 29
**Total Estimated Hours:** 88h

---

# 📊 Sprint Summary & Metrics

## Total Effort Estimation

| Sprint | Focus Area | Story Points | Est. Hours | Key Deliverables |
|--------|-----------|--------------|------------|------------------|
| Sprint 1 | Core Ride Booking | 19 | 56h | Ride requests, ride offers, wallet transactions |
| Sprint 2 | Matching & Lifecycle | 26 | 80h | Auto-matching, acceptance flow, ride lifecycle |
| Sprint 3 | Location & Real-Time | 26 | 80h | GPS tracking, WebSocket, ride progress |
| Sprint 4 | Safety Features | 23 | 72h | SOS system, emergency contacts, safety alerts |
| Sprint 5 | User Engagement | 24 | 72h | Ratings, notifications, messaging |
| Sprint 6 | Business & Production | 29 | 88h | Promotions, reports, admin tools, prod ready |
| **TOTAL** | **6 Sprints** | **147** | **448h** | **Full MVP** |

**Team Size Assumptions:**
- **2 Backend Developers:** ~80h per sprint (2 weeks × 40h)
- **Total Capacity:** ~160h per sprint
- **Buffer:** ~80h per sprint (50% buffer for testing, bug fixes, meetings)

---

## Risk Assessment

### 🔴 HIGH RISK
1. **Matching Algorithm Complexity** (Sprint 2)
   - **Risk:** Algorithm may not scale or provide good matches
   - **Mitigation:** Start with simple distance-based matching, iterate with AI later
   - **Fallback:** Manual driver selection by rider

2. **WebSocket Stability** (Sprint 3)
   - **Risk:** WebSocket connections may be unstable on mobile networks
   - **Mitigation:** Implement reconnection logic, graceful fallback to polling
   - **Fallback:** HTTP polling every 5 seconds for location updates

3. **Real-Time Location Performance** (Sprint 3)
   - **Risk:** High-frequency location updates may overload server
   - **Mitigation:** Implement throttling, caching (Redis), and rate limiting
   - **Fallback:** Reduce update frequency to 30 seconds

### 🟡 MEDIUM RISK
4. **Payment Integration** (Sprint 2)
   - **Risk:** Payment gateway (PayOS) integration may have issues
   - **Mitigation:** Already partially implemented, thorough testing needed
   - **Fallback:** Manual payment confirmation by admin

5. **SMS Notifications** (Sprint 4)
   - **Risk:** SMS service may not be configured
   - **Mitigation:** Email notifications already working
   - **Fallback:** Email-only notifications for MVP

### 🟢 LOW RISK
6. **Promotion System** (Sprint 6)
   - **Risk:** Complex validation logic
   - **Mitigation:** Well-defined requirements, straightforward implementation
   - **Fallback:** Manual discount application by admin

---

## Technical Debt & Future Enhancements

### Known Technical Debt
1. **PaymentService Interface** - Empty interface, needs implementation
2. **Background Check Integration** - Currently optional, should be required
3. **Redis Caching** - Not yet implemented for location/session data
4. **S3 File Storage** - Currently using local file system
5. **Email Templates** - Basic templates, need professional design

### Post-MVP Enhancements (Backlog)
1. **AI-Powered Matching** - Machine learning for better ride matching
2. **Route Optimization** - Multi-stop route planning
3. **Dynamic Pricing** - Surge pricing during peak hours
4. **Driver Earnings Dashboard** - Detailed analytics for drivers
5. **Ride Scheduling** - Book rides in advance
6. **Recurring Rides** - Daily commute subscriptions
7. **Social Features** - Favorite drivers, ride with friends
8. **Carbon Footprint Tracking** - Environmental impact metrics
9. **Loyalty Program** - Points and rewards
10. **Multi-Language Support** - Vietnamese + English

---

## Success Metrics (KPIs)

### Sprint-Level Metrics
- **Velocity:** Story points completed per sprint
- **Code Coverage:** Maintain > 80% test coverage
- **Bug Count:** < 5 critical bugs per sprint
- **Code Review Time:** < 24h turnaround
- **Deployment Frequency:** Weekly to staging, biweekly to production

### Product-Level Metrics (Post-Launch)
- **User Adoption:** 100+ active users in first month
- **Ride Completion Rate:** > 85% of accepted rides completed
- **Payment Success Rate:** > 95% of transactions successful
- **Average Response Time:** < 500ms for API endpoints
- **User Satisfaction:** > 4.0 average rating
- **Driver Utilization:** > 60% of drivers complete at least 1 ride per week

---

## Dependencies & Prerequisites

### External Dependencies
1. **Firebase Cloud Messaging (FCM)** - For mobile push notifications (Sprint 5)
2. **PayOS Payment Gateway** - Already integrated, needs production credentials
3. **SMS Service** - For emergency notifications (Sprint 4) - Optional for MVP
4. **Redis** - For caching (Sprint 3) - Recommended but not blocking

### Mobile Team Coordination
- **Sprint 1:** Mobile team needs API specs for ride requests
- **Sprint 2:** Mobile team integrates booking flow
- **Sprint 3:** Mobile team implements WebSocket client and location tracking
- **Sprint 4:** Mobile team implements SOS button UI
- **Sprint 5:** Mobile team integrates FCM for push notifications
- **Sprint 6:** Mobile team finalizes UI polish and testing

### Infrastructure Requirements
- **Database:** PostgreSQL 14+ with 50GB storage
- **Redis:** 4GB memory for caching (Sprint 3+)
- **Server:** 4 CPU cores, 8GB RAM minimum
- **File Storage:** 100GB for document uploads
- **SSL Certificate:** Required for production
- **Domain Name:** For API endpoint

---

## Deployment Strategy

### Sprint Milestones
- **Sprint 1 End:** Deploy to staging - Basic ride booking testable
- **Sprint 2 End:** Deploy to staging - Full ride lifecycle testable
- **Sprint 3 End:** Deploy to staging - Real-time features testable
- **Sprint 4 End:** Deploy to staging - Safety features testable
- **Sprint 5 End:** Deploy to staging - All features testable
- **Sprint 6 End:** **Deploy to PRODUCTION** - MVP launch

### Rollback Plan
- Maintain previous stable version in staging
- Database migrations reversible
- Feature flags for high-risk features
- Blue-green deployment for zero-downtime

---

## Team Ceremonies

### Daily Standup (15 min)
- What did I complete yesterday?
- What will I work on today?
- Any blockers?

### Sprint Planning (4 hours - Day 1 of sprint)
- Review backlog
- Estimate story points
- Commit to sprint goal
- Break down user stories into tasks

### Sprint Review/Demo (2 hours - Last day of sprint)
- Demo completed features
- Gather feedback
- Accept/reject user stories

### Sprint Retrospective (1.5 hours - Last day of sprint)
- What went well?
- What could be improved?
- Action items for next sprint

### Backlog Refinement (2 hours - Mid-sprint)
- Review upcoming stories
- Add acceptance criteria
- Estimate complexity

---

## Communication Plan

### Stakeholder Updates
- **Weekly:** Progress report to project sponsor
- **Biweekly:** Demo to stakeholders at sprint review
- **Monthly:** Metrics dashboard review

### Mobile Team Sync
- **Biweekly:** API contract review
- **As Needed:** WebSocket protocol updates
- **Sprint Planning:** Coordinate feature priorities

### Documentation Updates
- **Continuous:** Swagger API docs auto-updated
- **Sprint End:** Update README with new features
- **Major Releases:** Update architecture diagrams

---

# 🎯 Conclusion

This sprint planning provides a **realistic 12-week roadmap** to deliver a fully functional Motorbike Sharing System MVP. The plan prioritizes:

1. **Core Functionality First** (Sprints 1-2) - Without ride booking/matching, the app has no purpose
2. **Safety & Trust** (Sprints 3-4) - Critical for university student target audience
3. **User Experience** (Sprint 5) - Ratings, notifications, messaging enhance engagement
4. **Business Viability** (Sprint 6) - Promotions, analytics, admin tools for sustainable operation

**Key Success Factors:**
- ✅ Clear priorities and well-defined user stories
- ✅ Realistic time estimates with buffer
- ✅ Risk mitigation strategies
- ✅ Mobile team coordination
- ✅ Incremental delivery with staging deployments
- ✅ Comprehensive testing at each sprint

**Next Steps:**
1. Validate sprint plan with team
2. Set up development environment
3. Kickoff Sprint 1
4. Coordinate with mobile team for API contracts
5. Begin implementation! 🚀

---

**Document Version:** 1.0
**Last Updated:** 2025-10-08
**Author:** Claude Code Sprint Planning Assistant
**Status:** Ready for Team Review
