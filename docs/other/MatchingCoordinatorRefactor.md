# Ride Matching Coordinator Refactor – Phase 3 Design

## 1. Problem Recap
- `RideMatchingCoordinator` runs as an in-memory state machine per JVM. Sequential driver offers, timeouts, and fallback broadcast logic all live in a single service instance.
- `DriverDecisionGateway` tracks active offers in a local `ConcurrentHashMap`, making acceptance checks non-shareable across nodes and fragile during restarts.
- Timers rely on an in-process scheduler. If the node dies, matching sessions disappear and riders never receive final status updates.
- Scaling horizontally is unsafe: two nodes cannot cooperate because session state and timers are not shared. Load spikes at API level stall the matching executor.

**Goal for Phase 3:** move the orchestration loop onto RabbitMQ while persisting session state in Redis so that any app instance can pick up work and timers survive restarts. API contracts and synchronous behaviour observed by existing mobile clients must remain unchanged.

## 2. Functional Outcomes
1. **Event-Driven Matching Flow** – Ride creation publishes an event; the orchestrator consumes it, loads context, and pushes subsequent commands/events back to the queue.
2. **Shared Session Store** – Session metadata (current phase, notified drivers, deadlines) lives in Redis (hash per request). Redis becomes the source of truth for acceptance validation and timeout coordination.
3. **Timer Resiliency** – Instead of in-process `ScheduledExecutorService`, timeouts are scheduled via delayed RabbitMQ queues or a lightweight scheduler worker that re-enqueues timeout commands.
4. **Idempotent Consumers** – Each matching step must be retry-safe. If the same message is processed twice, the state stays consistent.
5. **Gradual Rollout** – Feature flag to fall back to the current synchronous coordinator until the MQ path proves stable.

## 3. Target Architecture

### 3.1 Components
- **Ride Matching Producer (existing)** – `SharedRideRequestServiceImpl` emits `RideRequestCreatedMessage` (already available from Phase 1).
- **Ride Matching Orchestrator Listener** – `@RabbitListener` consuming `ride.request.created`, `ride.matching.command.*`. Replaces direct call to `RideMatchingCoordinator.initiateMatching`.
- **Redis Session Repository**
  - Key: `ride:matching:session:{requestId}` (Hash)
  - Fields: `phase`, `notifiedDrivers` (sorted list), `activeOffer.driverId`, `activeOffer.rideId`, `offerDeadline`, `broadcast.deadline`, `requestDeadline`, `attempt`, etc.
  - TTL aligned with `requestAcceptTimeout` (e.g. 6 hours) for cleanup.
- **Command/Event Types**
  - `RideRequestCreated` – start matching.
  - `SendDriverOffer` – orchestrator instructs notification service to send next offer.
  - `DriverOfferTimeout` – produced by delay queue when driver response window expires.
  - `DriverResponseReceived` – emitted by driver acceptance endpoint.
  - `MatchingComplete` / `MatchingExpired` – final domain events for notifications and audit.
  - `EnterBroadcastMode` / `BroadcastTimeout` – cover fallback broadcast.
- **Driver Decision Validation**
  - Acceptance API publishes `DriverResponseReceived` to queue after persisting rider acceptance attempt. The orchestrator consumes it, validates against Redis state, and either confirms or rejects.
- **Notification Bridge**
  - Phase 4 will subscribe to `ride.matching.status.*`. For now, orchestrator still calls `RealTimeNotificationService`, but via events to avoid tight coupling.

### 3.2 Message Flow (AI Booking)
1. REST controller persists request; publishes `RideRequestCreated`.
2. Orchestrator loads request + proposals, stores session in Redis, enqueues `SendDriverOffer` for candidate #1.
3. On `SendDriverOffer`, orchestrator:
   - Persists active driver+deadline in Redis.
   - Emits `DriverOfferCreated` event (for notification service).
   - Schedules `DriverOfferTimeout` via TTL queue/delay.
4. Driver accepts -> API publishes `DriverResponseReceived` (with rideId, driverId).
5. Orchestrator handles response:
   - Validates driver matches Redis state and within deadline.
   - Runs `SharedRideRequestService.acceptRequest` (existing transactional logic).
   - On success: updates session phase to `COMPLETED`, emits `MatchingComplete` event, clears Redis state.
   - On failure: resets active offer, enqueues next `SendDriverOffer` (if proposals remain) or enters broadcast mode.
6. When `DriverOfferTimeout` triggers:
   - Orchestrator marks driver timed out, clears active offer, enqueues next `SendDriverOffer`.
7. If request deadline reached and no driver accepted: emit `MatchingExpired`, clear session.

### 3.3 Broadcast Mode
- Start broadcast by writing `phase=BROADCASTING` with `broadcastDeadline` in Redis.
- Enqueue `BroadcastOfferCreated` events (one per driver) rather than sequential messages to avoid lock contention—can reuse `DriverOfferCreated` with `broadcast=true` flag.
- Acceptances still go through `DriverResponseReceived` but flagged as broadcast. First successful acceptance marks session complete; others receive rejection response.
- Timeout handled by `BroadcastTimeout` delayed message.

### 3.4 Redis Data Model Sketch
```
HSET ride:matching:session:{requestId}
  phase MATCHING
  currentIndex 0
  notifiedDrivers [JSON array or sorted set key]
  activeOffer.driverId {driverId}
  activeOffer.rideId {rideId}
  activeOffer.deadline {epochMillis}
  requestDeadline {epochMillis}
  proposals {serialized list (Redis JSON) or pointer}
```
Supporting structures:
- `ride:matching:session:{requestId}:proposals` – Redis List/JSON storing minimal proposal data (driverId, rideId, score). Could also cache in DB table if size >1MB is a risk.
- `ride:matching:session:{requestId}:history` – optional list of state transitions for debugging.

### 3.5 Failure Handling
- **Duplicate messages**: store `lastProcessedMessageId` in Redis and skip if already handled.
- **Processing errors**: use Rabbit `DLQ` per queue. On recoverable errors, retry with exponential backoff (Rabbit plugin or manual requeue) and ensure idempotency.
- **Node crashes**: Redis retains session. New listener instance detects pending commands via queue. For outstanding driver offers, the timeout message still arrives and cleans up state.

## 4. Implementation Plan

### 4.1 Foundations
1. Add `RideMatchingCommand` enum/utility describing command types and routing keys (`ride.matching.command.*`).
2. Extend `RideMessagingProperties` with routing/queue names for matching commands + DLQs.
3. Enhance `RideMessagingConfiguration` to auto-declare command queues with appropriate dead-letter/delay setup.
4. Introduce `RideMatchingSessionRepository` abstraction over Redis (Spring Data Redis or Lettuce + `@Configuration`).
5. Implement serialization models for session state (`MatchingSessionState`, `ActiveOfferState`, `ProposalPointer`).

### 4.2 Listener + State Machine
1. Create `RideMatchingOrchestratorListener` with `@RabbitListener` methods:
   - `handleRideRequestCreated(RideRequestCreatedMessage)`
   - `handleSendDriverOffer(SendDriverOfferCommand)`
   - `handleDriverResponse(DriverResponseReceivedCommand)`
   - `handleDriverOfferTimeout(DriverOfferTimeoutCommand)`
   - `handleBroadcastTimeout(...)`
2. Move business logic from `RideMatchingCoordinator` into dedicated orchestration methods that operate on Redis state and call existing domain services (`RideMatchingService`, `SharedRideRequestService`, `RideFundCoordinatingService`, `RealTimeNotificationService`).
3. Keep existing coordinator implementation for JOIN and for fallback; behind a feature flag choose MQ orchestrator vs in-memory.

### 4.3 Driver Acceptance Path
1. Update `SharedRideRequestServiceImpl.acceptRequest` (and broadcast variants) to publish `DriverResponseReceivedCommand` to Rabbit instead of directly running coordinator callbacks when MQ is enabled.
2. For synchronous fallback, continue existing behaviour.
3. Ensure API response remains immediate. Accept endpoint should respond success only after orchestrator confirms acceptance. To maintain latency, orchestrator can perform acceptance synchronously and return a response message processed inline (listener running in same JVM). For cross-node scenario, consider a lightweight `MatchingResponseCache` keyed by response correlation ID for the REST layer to poll (future enhancement). For Phase 3, we run listener in same app, so acceptance can await orchestrator result via `CompletableFuture` held in an in-memory map keyed by correlationId.

### 4.4 Timers
1. Configure Rabbit delayed queues:
   - `ride.matching.delay.driver-offer` -> dead-letter to `ride.matching.command.driver-timeout`.
   - `ride.matching.delay.broadcast` -> dead-letter to `ride.matching.command.broadcast-timeout`.
2. On sending an offer, publish a message to delay queue with expiration equal to response window.
3. On broadcast start, publish to broadcast delay queue.

### 4.5 Feature Toggle & Migration
1. Add `app.messaging.ride.matching-enabled` property.
2. `SharedRideRequestServiceImpl` checks flag to decide whether to emit command or call in-memory coordinator.
3. Provide admin endpoint or configuration to switch on per-environment.
4. During dual-run, keep synchronous coordinator running but ensure double processing is avoided (only one mode active per environment).

### 4.6 Observability & Ops
1. Add `MeterRegistry` counters for processed commands, timeouts, retries, acceptances.
2. Log correlation: include `requestId` and `commandId` in logs while processing messages.
3. Expose actuator health indicator verifying Redis + Rabbit broker connectivity.

## 5. Estimated Iteration Breakdown
1. **Iteration A (Infrastructure)**
   - Properties, queues topology, session repository, command DTOs.
   - Feature flag scaffolding.
2. **Iteration B (Core Orchestrator)**
   - Implement listener, Redis-backed state transitions for sequential offers.
   - Integrate driver acceptance command & synchronous response bridging.
3. **Iteration C (Timeouts & Broadcast)**
   - Add delay queues, handle broadcast mode, ensure idempotency.
4. **Iteration D (Cleanup & Observability)**
   - Metrics, logging, TTL cleanup, fallback toggle wiring, integration tests.

## 6. Testing Strategy
- **Unit** – Redis repository (mocked connection), command handlers, idempotent acceptance checks.
- **Integration** – Using Testcontainers RabbitMQ + Redis to simulate full flow (create request -> driver accept -> confirm). If Testcontainers is heavy for regular runs, mark as `@Tag("integration")`.
- **Load/Chaos** – Stress-run orchestrator with simultaneous requests to confirm Redis + queue handling, simulate listener restart mid-session.
- **Rollback path** – Toggle off MQ flag to revert to legacy flow; must keep both code paths functional until full rollout.

## 7. Risks & Mitigations
- **Complexity of synchronous acceptance response** – Start by running orchestrator in same app instance and bridge via `CompletableFuture` to keep API latency low. Evaluate cross-node solution later.
- **Redis data consistency** – Protect with `WATCH/MULTI` or Lua scripts for atomic updates. Alternatively, use Lettuce `execute` with `SETNX`/`HSET` semantics.
- **Message explosion during broadcast** – Limit broadcast to top N drivers or throttle publish rate; queue bindings already allow scaling via consumer concurrency.
- **Operational overhead** – Provide scripts/dashboards showing queue depth and orphan sessions (Redis TTL ensures eventual cleanup).

---
Prepared for Phase 3 implementation. Once agreed, we can start Iteration A focusing on infrastructure scaffolding and toggles.
