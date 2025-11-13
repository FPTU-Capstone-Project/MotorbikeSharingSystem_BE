# Queue-Based Ride Processing System – Implementation Guide

_Last updated: November 2025_

This document explains the **queue-based ride matching and real-time processing architecture** implemented to replace the legacy in-memory, per-thread coordinator. It covers design principles, architecture, message flow, state management, failure handling, and operational considerations. Written for teammates, reviewers, and anyone who needs to understand or explain the distributed real-time processing system to project stakeholders.

---

## 1. Executive Summary

### 1.1 What Was Built

The system was refactored from an **in-memory, single-node, async executor-based matching coordinator** to a **message queue-driven, horizontally scalable, Redis-backed orchestration system**. The new design:

- Uses **RabbitMQ** as the event and command bus
- Persists matching session state in **Redis** for cross-instance visibility and crash recovery
- Maintains **backwards compatibility** with existing mobile clients and REST APIs
- Provides a **feature flag** to fall back to the legacy coordinator if needed
- Handles both **sequential (ranked)** and **broadcast (all-drivers)** matching phases
- Supports **JOIN_RIDE** and **BOOKING** request types with unified logic

### 1.2 Why This Design

**Problem with Legacy System:**
- State trapped in JVM memory → crashes lose all matching sessions
- Executors and schedulers don't scale horizontally
- No shared coordination → multiple nodes would conflict
- Tight coupling between API threads and matching logic

**Solution Goals:**
- **Scalability**: any instance can process any message
- **Resiliency**: crashes don't lose state; timers survive restarts
- **Observability**: messages are durable audit trails
- **Maintainability**: clear separation of concerns via pub/sub

---

## 2. Design Principles

### 2.1 Event-Driven Orchestration

Instead of direct method calls, components communicate via **events** and **commands** published to RabbitMQ:

- **Events** describe facts (e.g., `RideRequestCreated`, `DriverResponseReceived`)
- **Commands** instruct actions (e.g., `SendNextOffer`, `DriverTimeout`)

**Benefits:**
- Decouples producers from consumers
- Natural async processing boundary
- Easy to add new listeners without changing existing code

### 2.2 Shared State in Redis

All matching session metadata lives in Redis with a TTL:

- **Key pattern**: `ride:matching:session:{requestId}`
- **TTL**: Matches the total matching timeout (default 15 minutes)
- **Serialization**: JSON via Jackson with polymorphic type hints

Any application instance can load, modify, and save session state, enabling:
- Horizontal scaling
- Crash recovery
- Multi-region deployments (future)

### 2.3 Idempotency First

Every message handler checks if the work was already done before proceeding:

- **Correlation IDs** track unique messages
- **State checks** prevent double-processing (e.g., session already `COMPLETED`)
- **Redis atomic operations** where needed

This ensures retry-safety: if RabbitMQ redelivers a message, the system stays consistent.

### 2.4 Graceful Degradation

The system includes multiple fallback mechanisms:

- **Feature flags** to disable MQ mode and use legacy coordinator
- **Direct notification calls** when MQ notification publisher unavailable
- **DLQ (Dead Letter Queue)** for unprocessable messages
- **Health indicators** expose connectivity issues to ops dashboards

---

## 3. Architecture Overview

### 3.1 High-Level Components

```
┌─────────────────┐       ┌──────────────────┐       ┌─────────────────┐
│  REST API       │       │   RabbitMQ       │       │  Redis          │
│  Controllers    │──────▶│   Exchange       │◀──────│  Session Store  │
│  (Rider/Driver) │       │   + Queues       │       │  (State + TTL)  │
└─────────────────┘       └──────────────────┘       └─────────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │  Queue Orchestrator          │
                  │  (@RabbitListener)           │
                  │  - onRideRequestCreated      │
                  │  - onMatchingCommand         │
                  └──────────────────────────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         ▼                       ▼                       ▼
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ Notification     │   │ Matching Service │   │ Decision Gateway │
│ Publishers       │   │ (Scoring)        │   │ (Timeout Mgmt)   │
└──────────────────┘   └──────────────────┘   └──────────────────┘
```

### 3.2 Core Classes

| Class | Package | Responsibility |
|-------|---------|----------------|
| `QueueRideMatchingOrchestrator` | `service.domain.matching` | Central state machine consuming events and commands from RabbitMQ |
| `MatchingSessionState` | `service.domain.matching.session` | Redis-serializable POJO representing session data |
| `RedisMatchingSessionRepository` | `service.domain.matching.session` | Redis persistence layer for session state |
| `RideMatchingCommandPublisher` | `messaging` | Publishes matching commands to RabbitMQ queues |
| `RideNotificationEventPublisher` | `messaging` | Publishes notification events to RabbitMQ |
| `RideNotificationEventListener` | `service.domain.matching` | Consumes notification messages and dispatches via `RealTimeNotificationService` |
| `DriverLocationUpdateListener` | `messaging.listener` | Consumes real-time driver location updates (future ETA recalculation) |
| `RideMatchingHealthIndicator` | `actuator` | Exposes health status for RabbitMQ + Redis connectivity |
| `RideMessagingProperties` | `infrastructure.config.properties` | Configuration for exchanges, queues, timeouts, and feature flags |
| `ApplicationEventPublisherService` | `service` | Facade for publishing ride events (delegates to MQ or sync publisher) |

---

## 4. Message Flow & Queue Topology

### 4.1 RabbitMQ Exchange and Queues

**Exchange:** `ride.events` (type: `topic`)

**Queues and Routing Keys:**

| Queue Name | Routing Key | Purpose |
|------------|-------------|---------|
| `ride.request.created.queue` | `ride.request.created` | Entry point for new ride requests (BOOKING or JOIN_RIDE) |
| `ride.matching.command.queue` | `ride.matching.command` | Commands for orchestrator (send next offer, driver timeout, etc.) |
| `ride.matching.delay.driver-timeout` | N/A (delay queue) | Scheduled driver offer timeouts (dead-letters back to command queue) |
| `ride.matching.delay.broadcast-timeout` | N/A (delay queue) | Scheduled broadcast phase timeouts |
| `ride.notifications.queue` | `ride.notifications` | Notification messages for drivers and riders |
| `ride.location.driver.queue` | `ride.location.driver` | Real-time driver location updates |
| `ride.matching.dlq` | N/A (manual routing) | Dead letter queue for failed message processing |

**Delay Queue Mechanism:**
- Messages published to `ride.matching.delay.driver-timeout` with `x-message-ttl` set to response window (e.g., 90 seconds)
- When TTL expires, message is dead-lettered to `ride.matching.command.queue` with routing key `ride.matching.command`
- Orchestrator processes the timeout command as if it were just sent

### 4.2 Message Types

#### 4.2.1 Events (Domain Facts)

**`RideRequestCreatedMessage`**
```json
{
  "requestId": 42,
  "occurredAt": "2025-11-04T10:30:00Z",
  "correlationId": "uuid-123"
}
```
Published by: `ApplicationEventPublisherService` (via `RabbitRideEventPublisher`)
Consumed by: `QueueRideMatchingOrchestrator.onRideRequestCreated`

**`DriverLocationUpdateMessage`**
```json
{
  "driverId": 12,
  "rideId": 8,
  "latitude": 10.762622,
  "longitude": 106.660172,
  "timestamp": "2025-11-04T10:35:00Z"
}
```
Published by: `RideTrackingServiceImpl` during active rides
Consumed by: `DriverLocationUpdateListener` (future ETA updates)

#### 4.2.2 Commands (Orchestration Instructions)

**`MatchingCommandMessage`** (polymorphic based on `commandType`)

| Command Type | Purpose | Payload Fields |
|--------------|---------|----------------|
| `SEND_NEXT_OFFER` | Trigger next driver offer | `requestId`, `candidateIndex` |
| `DRIVER_TIMEOUT` | Handle driver response timeout | `requestId`, `driverId`, `rideId` |
| `DRIVER_RESPONSE` | Process driver acceptance | `requestId`, `driverId`, `rideId`, `broadcast` flag |
| `BROADCAST_TIMEOUT` | Handle broadcast phase expiry | `requestId` |
| `CANCEL_MATCHING` | Stop matching (rider cancelled) | `requestId` |

**Example: `SEND_NEXT_OFFER`**
```json
{
  "commandType": "SEND_NEXT_OFFER",
  "requestId": 42,
  "candidateIndex": 1,
  "occurredAt": "2025-11-04T10:30:05Z",
  "correlationId": "uuid-456"
}
```

#### 4.2.3 Notifications (UI Updates)

**`MatchingNotificationMessage`**

| Notification Type | Target | Payload Type |
|-------------------|--------|--------------|
| `DRIVER_OFFER` | Driver | `DriverRideOfferNotification` |
| `RIDER_STATUS` | Rider | `RiderMatchStatusNotification` |

Published by: `RideNotificationEventPublisher`
Consumed by: `RideNotificationEventListener`
Final delivery: `RealTimeNotificationService` → WebSocket (STOMP) + Database

---

## 5. State Management Deep Dive

### 5.1 MatchingSessionState Structure

**Core Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `requestId` | `Integer` | Primary key (maps to `SharedRideRequest.id`) |
| `phase` | `MatchingSessionPhase` | Current stage (see §5.2) |
| `proposals` | `List<RideMatchProposalResponse>` | Ranked candidate rides (sequential mode) |
| `nextProposalIndex` | `int` | Cursor for sequential offer loop |
| `activeOffer` | `ActiveOfferState` | Currently pending driver/ride pair |
| `notifiedDrivers` | `Set<Integer>` | Drivers already contacted (for broadcast eligibility) |
| `requestDeadline` | `Instant` | Overall matching timeout (15 min from request creation) |
| `broadcastDeadline` | `Instant` | Broadcast phase deadline (if entered) |
| `lastProcessedMessageId` | `String` | Correlation ID for idempotency |
| `lastProcessedAt` | `Instant` | Timestamp of last message processed |

**Redis Key:** `ride:matching:session:{requestId}`

**TTL:** Set to `app.messaging.ride.matching-request-timeout` (default `PT15M`)

**Serialization:** Jackson JSON with `@JsonFormat(shape = STRING)` for `Instant` fields and polymorphic type info enabled

### 5.2 Matching Phase State Machine

```
                  ┌─────────────┐
                  │  MATCHING   │  Initial state
                  └──────┬──────┘
                         │ offer sent to driver
                         ▼
            ┌──────────────────────┐
            │ AWAITING_CONFIRMATION │  Driver has active offer
            └──────┬────────────────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   [timeout]   [reject]   [accept]
        │          │          │
        └──────────┴──────────┤
                   │           └──────▶ ┌───────────┐
                   │                    │ COMPLETED │
                   │                    └───────────┘
                   ▼
              [no more candidates]
                   │
                   ▼
            ┌──────────────┐
            │ BROADCASTING │  Offer sent to all eligible drivers
            └──────┬───────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
   [timeout]   [reject]   [accept]
        │          │          │
        │          │          └──────▶ ┌───────────┐
        │          │                   │ COMPLETED │
        │          │                   └───────────┘
        └──────────┴─────────▶ ┌───────────┐
                               │  EXPIRED  │
                               └───────────┘

                              ┌────────────┐
          [rider cancels]  ─▶ │ CANCELLED  │
                              └────────────┘
```

**Phase Transitions:**

1. **`MATCHING`**: Sequential offers being sent to ranked drivers
2. **`AWAITING_CONFIRMATION`**: Driver has an active offer, waiting for response
3. **`BROADCASTING`**: All eligible drivers notified simultaneously

> **Marketplace-Only Broadcast:** When no eligible drivers are available for push notifications, the orchestrator still transitions the request/session into `BROADCASTING` so it remains discoverable from the driver marketplace feed while skipping outbound offers. This prevents requests from getting stuck in `PENDING` once sequential matching is exhausted.
4. **`COMPLETED`**: Driver accepted and request confirmed
5. **`EXPIRED`**: No driver accepted within timeout
6. **`CANCELLED`**: Rider cancelled the request

### 5.3 Active Offer Validation

**`ActiveOfferState`** tracks the current pending offer:

```java
{
  "driverId": 12,
  "rideId": 8,
  "expiresAt": "2025-11-04T10:31:30Z"
}
```

**When driver responds:**
1. Orchestrator loads session from Redis
2. Checks `activeOffer.matches(rideId, driverId)`
3. Checks `Instant.now() < expiresAt`
4. If both pass → process acceptance, else → reject as expired

**For broadcast mode:**
- No single `activeOffer` (multiple drivers notified)
- Validation uses `notifiedDrivers.contains(driverId)` instead
- First acceptance wins, others rejected

### 5.4 Idempotency Implementation

**Problem:** RabbitMQ may redeliver messages (network issues, crashes, etc.)

**Solution:** Store last processed `correlationId` in session state

```java
public boolean shouldProcess(String messageId) {
    if (messageId == null) return true; // Allow processing
    if (messageId.equals(lastProcessedMessageId)) return false; // Duplicate
    
    lastProcessedMessageId = messageId;
    lastProcessedAt = Instant.now();
    return true;
}
```

**Usage in orchestrator:**
```java
if (!state.shouldProcess(command.getCorrelationId())) {
    log.debug("Skipping duplicate message {} for request {}", 
        command.getCorrelationId(), command.getRequestId());
    return;
}
// ... process command ...
sessionRepository.save(state, sessionTtl()); // Persist idempotency marker
```

---

### 5.5 Stale Session Detection

Local developers frequently reset the PostgreSQL database while leaving Redis untouched. Because Redis keys are keyed by `requestId`, an old session such as `ride:matching:session:1` can linger and cause the orchestrator to treat a brand-new ride request as a duplicate.  

To prevent that silent no-op:

- Every session snapshot now stores `requestCreatedAt` (captured from the DB row).
- When a new `ride.request.created` event arrives, the orchestrator compares the DB timestamp with the Redis value.
- If the database record is newer by more than a small skew, the Redis entry is considered stale; it is deleted and matching restarts from scratch.

This automatic purge keeps the queue-driven flow working even after DB truncations without requiring operators to flush Redis manually.

---

## 6. Detailed Flow Diagrams

### 6.1 BOOKING Request Flow (Sequential → Broadcast)

**Scenario:** Rider creates AI booking, system tries 2 ranked drivers, then broadcasts to all

```
┌──────┐                 ┌─────────┐              ┌──────────┐             ┌───────┐
│Rider │                 │  API    │              │RabbitMQ  │             │ Redis │
└──┬───┘                 └────┬────┘              └────┬─────┘             └───┬───┘
   │                          │                        │                       │
   │ POST /ride-requests      │                        │                       │
   ├─────────────────────────▶│                        │                       │
   │                          │ Save request (PENDING) │                       │
   │                          ├───────────────────────────────────────────────▶│
   │                          │                        │                       │
   │                          │ Publish RideRequestCreatedMessage              │
   │                          ├───────────────────────▶│                       │
   │ 201 Created              │                        │                       │
   │◀─────────────────────────┤                        │                       │
   │                          │                        │                       │
   │                          │           [Orchestrator consumes message]      │
   │                          │                        │                       │
   │                          │                    Call RideMatchingService    │
   │                          │                    (score & rank drivers)      │
   │                          │                        │                       │
   │                          │                    Initialize session          │
   │                          │                    (proposals=[D1, D2])       │
   │                          │                        ├──────────────────────▶│
   │                          │                        │ Save session          │
   │                          │                        │                       │
   │                          │                    Publish SEND_NEXT_OFFER(0)  │
   │                          │                        │                       │
   │                          │           [Orchestrator handles SEND_NEXT]     │
   │                          │                        │                       │
   │                          │                    Load session from Redis     │
   │                          │                        │◀──────────────────────│
   │                          │                        │                       │
   │                          │                    Send offer to Driver 1      │
   │                          │                    Set activeOffer(D1, R8)     │
   │                          │                        ├──────────────────────▶│
   │                          │                        │ Update session        │
   │                          │                        │                       │
   │                          │                    Publish timeout(90s)        │
   │                          │                    to delay queue              │
   │                          │                        │                       │
┌────────┐                   │                        │                       │
│Driver1 │ receives offer    │                        │                       │
└────┬───┘ via WebSocket     │                        │                       │
     │                       │                        │                       │
     │ [waits 90s... timeout]│                        │                       │
     │                       │                        │                       │
     │                       │           [Delay queue expires]                │
     │                       │                        │                       │
     │                       │           [Orchestrator handles DRIVER_TIMEOUT] │
     │                       │                        │                       │
     │                       │                    Clear activeOffer            │
     │                       │                    Publish SEND_NEXT_OFFER(1)   │
     │                       │                        │                       │
     │                       │           [Orchestrator handles SEND_NEXT]     │
     │                       │                        │                       │
     │                       │                    Send offer to Driver 2      │
     │                       │                    Set activeOffer(D2, R9)     │
     │                       │                        │                       │
┌────────┐                  │                        │                       │
│Driver2 │ receives offer    │                        │                       │
└────┬───┘                   │                        │                       │
     │ [timeout again]       │                        │                       │
     │                       │                        │                       │
     │                       │           [No more proposals → broadcast]      │
     │                       │                        │                       │
     │                       │                    Calculate remaining time     │
     │                       │                    (15 min - time spent)       │
     │                       │                    = 13 minutes left            │
     │                       │                        │                       │
     │                       │                    Query all eligible drivers   │
     │                       │                    (ACTIVE, not in notifiedDrivers) │
     │                       │                        │                       │
     │                       │                    Set phase = BROADCASTING     │
     │                       │                        ├──────────────────────▶│
     │                       │                        │                       │
     │                       │                    Send offer to D3, D4, D5... │
     │                       │                    (all with 13-min deadline)  │
     │                       │                        │                       │
┌────────┐                  │                        │                       │
│Driver3 │ receives broadcast│                       │                       │
└────┬───┘                   │                        │                       │
     │ POST /accept          │                        │                       │
     ├──────────────────────▶│                        │                       │
     │                       │ Update request (CONFIRMED)                     │
     │                       │ Publish DRIVER_RESPONSE                        │
     │                       ├───────────────────────▶│                       │
     │ 200 OK                │                        │                       │
     │◀──────────────────────┤                        │                       │
     │                       │           [Orchestrator handles DRIVER_RESPONSE]│
     │                       │                        │                       │
     │                       │                    Validate driver in notifiedDrivers│
     │                       │                    Mark session COMPLETED       │
     │                       │                        ├──────────────────────▶│
     │                       │                        │                       │
     │                       │                    Send rider notification      │
   ┌─┴──┐                   │                        │                       │
   │Rider│ receives "Driver  │                        │                       │
   └────┘ accepted" via WS   │                        │                       │
```

### 6.2 JOIN_RIDE Request Flow

**Scenario:** Rider directly joins an existing shared ride

**Key Difference from BOOKING:** JOIN requests target a specific driver/ride. If the driver times out or rejects, the request **immediately fails** and releases the wallet hold. **NO broadcast phase, NO retry to other drivers.**

```
┌──────┐                 ┌─────────┐              ┌──────────┐             ┌───────┐
│Rider │                 │  API    │              │RabbitMQ  │             │ Redis │
└──┬───┘                 └────┬────┘              └────┬─────┘             └───┬───┘
   │                          │                        │                       │
   │ POST /shared-rides/8/join│                        │                       │
   ├─────────────────────────▶│                        │                       │
   │                          │ Create JOIN request    │                       │
   │                          │ (sharedRide=8, PENDING)│                       │
   │                          │                        │                       │
   │                          │ Publish RideRequestCreatedMessage              │
   │                          ├───────────────────────▶│                       │
   │ 201 Created              │                        │                       │
   │◀─────────────────────────┤                        │                       │
   │                          │                        │                       │
   │                          │           [Orchestrator consumes message]      │
   │                          │                        │                       │
   │                          │                    Detect requestKind=JOIN_RIDE│
   │                          │                    Fetch ride & driver info    │
   │                          │                        │                       │
   │                          │                    Initialize session          │
   │                          │                    (requestKind=JOIN_RIDE)     │
   │                          │                        ├──────────────────────▶│
   │                          │                        │                       │
   │                          │                    Send offer to driver        │
   │                          │                    Set activeOffer(D12, R8)    │
   │                          │                        ├──────────────────────▶│
   │                          │                        │                       │
   │                          │                    Register timeout (90s)      │
   │                          │                        │                       │
┌────────┐                   │                        │                       │
│Driver12│ receives offer    │                        │                       │
└────┬───┘                   │                        │                       │
     │                       │                        │                       │
     │ [CASE 1: Driver Accepts]                      │                       │
     │ POST /accept          │                        │                       │
     ├──────────────────────▶│                        │                       │
     │                       │ Update request (CONFIRMED)                     │
     │                       │ Publish DRIVER_RESPONSE                        │
     │                       ├───────────────────────▶│                       │
     │ 200 OK                │                        │                       │
     │◀──────────────────────┤                        │                       │
     │                       │           [Orchestrator handles DRIVER_RESPONSE]│
     │                       │                        │                       │
     │                       │                    Load session, validate activeOffer│
     │                       │                    Mark COMPLETED               │
     │                       │                        ├──────────────────────▶│
     │                       │                        │                       │
     │                       │                    Send rider notification      │
     │                       │                    (JOIN success)               │
   ┌─┴──┐                   │                        │                       │
   │Rider│ receives "Driver  │                        │                       │
   └────┘ accepted" via WS   │                        │                       │
     │                       │                        │                       │
     │                       │                        │                       │
     │ [CASE 2: Driver Timeout - 90s expires]        │                       │
     │                       │                        │                       │
     │                       │           [Timeout message expires]            │
     │                       │                        │                       │
     │                       │           [Orchestrator handles DRIVER_TIMEOUT]│
     │                       │                        │                       │
     │                       │                    Detect requestKind=JOIN_RIDE│
     │                       │                    Mark EXPIRED (no retry!)     │
     │                       │                        ├──────────────────────▶│
     │                       │                        │                       │
     │                       │                    Send rider notification      │
     │                       │                    (JOIN failed - timeout)     │
   ┌─┴──┐                   │                        │                       │
   │Rider│ receives "Driver  │                        │                       │
   └────┘ did not respond"   │                        │                       │
          via WS             │                        │                       │
```

**Critical Behavior:**
- ✅ JOIN requests have `requestKind = JOIN_RIDE` stored in session
- ✅ If driver times out → immediate failure notification to rider
- ✅ If driver rejects → immediate failure notification to rider
- ❌ **NEVER enters broadcast mode** (only BOOKING requests broadcast)
- ❌ **NEVER tries next candidate** (JOIN targets one specific driver)
- ✅ Wallet hold released immediately on failure

---

## 7. Technical Implementation Details

### 7.1 RabbitMQ Configuration

**Spring AMQP Setup:**

```yaml
# application.properties
spring.rabbitmq.host=chameleon.lmq.cloudamqp.com
spring.rabbitmq.port=5671
spring.rabbitmq.username=yhwvnbjj
spring.rabbitmq.password=***
spring.rabbitmq.virtual-host=yhwvnbjj
spring.rabbitmq.ssl.enabled=true
```

**Topology Declaration (automatic):**

Queues, exchanges, and bindings are auto-declared by Spring AMQP on startup based on `@Bean` definitions in configuration classes.

**Delay Queue Configuration:**

```java
Queue driverTimeoutDelayQueue = QueueBuilder
    .durable("ride.matching.delay.driver-timeout")
    .withArgument("x-dead-letter-exchange", "ride.events")
    .withArgument("x-dead-letter-routing-key", "ride.matching.command")
    .build();
```

When a message is published with TTL:
```java
MessagePostProcessor processor = message -> {
    message.getMessageProperties().setExpiration(
        String.valueOf(duration.toMillis()));
    return message;
};
rabbitTemplate.convertAndSend(queueName, command, processor);
```

### 7.2 Redis Configuration

**Connection:**

```yaml
spring.data.redis.url=rediss://red-d448uv8dl3ps73anism0:***@oregon-keyvalue.render.com:6379
spring.data.redis.ssl.enabled=true
```

**Serialization:**

```java
@Bean
public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(
        new GenericJackson2JsonRedisSerializer(jacksonObjectMapper()));
    return template;
}

private ObjectMapper jacksonObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    // Enable polymorphic type handling for complex objects
    BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator
        .builder()
        .allowIfSubType(Object.class)
        .build();
    mapper.activateDefaultTyping(validator, 
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);
    return mapper;
}
```

**Session Repository:**

```java
@Repository
public class RedisMatchingSessionRepository {
    private static final String KEY_PREFIX = "ride:matching:session:";
    private final RedisTemplate<String, Object> redisTemplate;
    
    public Optional<MatchingSessionState> find(Integer requestId) {
        String key = KEY_PREFIX + requestId;
        Object value = redisTemplate.opsForValue().get(key);
        return value instanceof MatchingSessionState state 
            ? Optional.of(state) 
            : Optional.empty();
    }
    
    public void save(MatchingSessionState state, Duration ttl) {
        String key = KEY_PREFIX + state.getRequestId();
        redisTemplate.opsForValue().set(key, state, ttl);
    }
}
```

### 7.3 Message Listener Configuration

**Orchestrator Listeners:**

```java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.messaging.ride", 
    name = {"enabled", "matching-enabled"}, 
    havingValue = "true")
public class QueueRideMatchingOrchestrator {
    
    @RabbitListener(
        queues = "${app.messaging.ride.ride-request-created-queue}", 
        autoStartup = "true")
    public void onRideRequestCreated(@Payload RideRequestCreatedMessage msg) {
        // ... handle request creation
    }
    
    @RabbitListener(
        queues = "${app.messaging.ride.matching-command-queue}", 
        autoStartup = "true",
        concurrency = "1") // Single consumer for ordering
    public void onMatchingCommand(@Payload MatchingCommandMessage cmd) {
        // ... handle commands
    }
}
```

**Concurrency Note:**
- `ride.request.created.queue`: Can have multiple consumers (requests independent)
- `ride.matching.command.queue`: Single consumer per request (ensured by design, not queue config)
- Commands for different requests can process in parallel
- Commands for same request are idempotent and state-checked

### 7.4 Notification Flow

**Two-Tier Architecture:**

1. **Orchestrator → MQ Notification Publisher** (if `notificationsEnabled = true`)
2. **MQ Notification Listener → RealTimeNotificationService** (always)

**Why the indirection?**
- Decouples orchestrator from WebSocket layer
- Allows future notification microservice
- Easier to monitor notification delivery separately

**Code Flow:**

```java
// In QueueRideMatchingOrchestrator
private void dispatchRiderStatus(SharedRideRequest request, 
                                  RiderMatchStatusNotification payload) {
    if (properties.isNotificationsEnabled()) {
        // Publish to MQ
        notificationPublisher.publishRiderStatus(
            request.getSharedRideRequestId(),
            request.getRider().getUser().getUserId(),
            payload);
    } else {
        // Direct call (fallback)
        notificationService.notifyRiderStatus(
            request.getRider().getUser(), payload);
    }
}
```

```java
// In RideNotificationEventListener
@RabbitListener(queues = "${app.messaging.ride.notification-queue}")
public void onNotification(@Payload MatchingNotificationMessage msg) {
    if (msg.getType() == RIDER_STATUS) {
        User rider = userRepository.findById(msg.getRiderUserId()).get();
        notificationService.notifyRiderStatus(rider, msg.getRiderPayload());
    }
}
```

---

## 8. Error Handling & Resiliency

### 8.1 Retry Strategy

**RabbitMQ Level:**
- Messages auto-acked only after successful processing
- On exception, message redelivered (up to `spring.rabbitmq.listener.simple.retry.max-attempts`)
- After max retries → moved to DLQ

**Application Level:**
- Idempotency checks prevent duplicate work
- State validation prevents invalid transitions (e.g., can't complete already-completed session)

### 8.2 Dead Letter Queue (DLQ)

**Queue:** `ride.matching.dlq`

**When messages go to DLQ:**
- Unhandled exceptions after max retries
- Corrupt message data (deserialization failures)
- Business logic errors (e.g., request not found in DB)

**Manual Intervention:**
- Ops team monitors DLQ depth via RabbitMQ management UI
- Messages inspected, issue diagnosed (DB inconsistency, bug, etc.)
- Can manually re-publish corrected messages to original queue

**Future Enhancement:**
- Automated DLQ replay for transient errors
- Alerting on DLQ depth > threshold

### 8.3 Timeout Handling

**Scenario:** Driver response timeout (90 seconds)

1. Orchestrator publishes timeout command to delay queue with TTL
2. Delay queue holds message for 90 seconds
3. Message expires → dead-lettered to command queue
4. Orchestrator consumes `DRIVER_TIMEOUT` command
5. Validates `activeOffer` still matches (not superseded by acceptance)
6. Clears `activeOffer`, publishes `SEND_NEXT_OFFER` for next candidate

**Crash Recovery:**
- Timeout messages persist in delay queue even if app crashes
- When app restarts, delay queue still expires and delivers timeout
- Session state in Redis ensures orchestrator knows where it left off

### 8.4 Redis Failure

**Detection:**
- `RideMatchingHealthIndicator` pings Redis every health check
- If Redis down → health endpoint returns `503`, load balancer can route away

**Impact:**
- New ride requests cannot create sessions → fail fast with `503`
- In-progress sessions lost (TTL-based cleanup)
- Legacy coordinator can be enabled as fallback (`app.messaging.ride.matching-enabled=false`)

**Mitigation:**
- Redis replication (master-slave setup)
- Redis cluster for high availability
- Monitor Redis memory usage (sessions are TTL'd to prevent OOM)

### 8.5 RabbitMQ Failure

**Detection:**
- Health indicator creates/closes test connection
- Consumer listeners auto-reconnect via Spring AMQP

**Impact:**
- Events/commands cannot be published → REST API returns errors
- In-flight messages safe in queues (durable)

**Mitigation:**
- RabbitMQ cluster with mirrored queues
- Persistent messages (non-transient)
- Connection retry with exponential backoff (Spring AMQP default)

---

## 9. Configuration Reference

### 9.1 Feature Flags

| Property | Default | Description |
|----------|---------|-------------|
| `app.messaging.ride.enabled` | `false` | Master switch for RabbitMQ integration |
| `app.messaging.ride.matching-enabled` | `false` | Enable queue-based orchestrator (vs. legacy) |
| `app.messaging.ride.notifications-enabled` | `false` | Publish notifications via MQ (vs. direct calls) |

**Recommended Rollout:**
1. `enabled=true`, `matching-enabled=false`, `notifications-enabled=false` → Test event publishing
2. `enabled=true`, `matching-enabled=true`, `notifications-enabled=false` → Test orchestrator with direct notifications
3. `enabled=true`, `matching-enabled=true`, `notifications-enabled=true` → Full MQ mode

### 9.2 Timeout Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.ride.request-acceptance-timeout` | `PT15M` | Total matching window (sequential + broadcast) |
| `app.messaging.ride.matching-request-timeout` | `PT15M` | Redis session TTL (should match above) |
| `app.messaging.ride.driver-response-window` | `PT90S` | Driver response timeout (sequential phase) |
| `app.ride.broadcast.response-window-seconds` | `30` | Individual driver timeout during broadcast |
| `app.ride.matching.driver-response-seconds` | `90` | Legacy coordinator timeout (kept for fallback) |

**Calculation Example:**
- Total timeout: 15 minutes
- Sequential phase: 3 drivers × 90s = 4.5 minutes
- Broadcast phase: 15 - 4.5 = 10.5 minutes remaining
- Each driver in broadcast has 30s individual timeout (but broadcast ends when 10.5 min total expires)

### 9.3 Queue Names

All queue/exchange names configurable via `RideMessagingProperties`:

```yaml
app.messaging.ride:
  exchange: ride.events
  ride-request-created-queue: ride.request.created.queue
  matching-command-queue: ride.matching.command.queue
  notification-queue: ride.notifications.queue
  driver-location-queue: ride.location.driver.queue
  driver-timeout-delay-queue: ride.matching.delay.driver-timeout
  broadcast-timeout-delay-queue: ride.matching.delay.broadcast-timeout
```

**Custom Deployment:**
Change queue names to avoid conflicts in shared RabbitMQ environments (e.g., staging vs. production)

---

## 10. Observability & Monitoring

### 10.1 Health Checks

**Endpoint:** `GET /actuator/health/rideMatching`

**Response (healthy):**
```json
{
  "status": "UP",
  "details": {
    "rabbitmq": "UP",
    "redis": "UP",
    "activeSessions": 12
  }
}
```

**Response (degraded):**
```json
{
  "status": "DOWN",
  "details": {
    "rabbitmq": "DOWN - Connection refused",
    "redis": "UP",
    "error": "RabbitMQ unavailable"
  }
}
```

**Implementation:**
```java
@Component("rideMatching")
@ConditionalOnProperty(
    prefix = "app.messaging.ride", 
    name = "enabled", 
    havingValue = "true")
public class RideMatchingHealthIndicator implements HealthIndicator {
    
    public Health health() {
        Health.Builder builder = Health.up();
        checkRabbitMQ(builder);
        checkRedis(builder);
        return builder.build();
    }
}
```

### 10.2 Metrics (Micrometer)

**Metrics Recorded:**

| Metric Name | Type | Tags | Description |
|-------------|------|------|-------------|
| `ride.matching.booking.created` | Counter | `outcome=success\|no_candidates` | Booking requests processed |
| `ride.matching.driver.response` | Counter | `outcome=accepted\|ignored` | Driver responses received |
| `ride.matching.match.completed` | Counter | `type=sequential\|broadcast` | Successful matches |

**Usage:**
```java
private void recordMetric(String metricName, String outcome) {
    meterRegistry.counter(
        "ride.matching." + metricName, 
        "outcome", outcome
    ).increment();
}
```

**Future Metrics:**
- `ride.matching.session.duration` (timer)
- `ride.matching.broadcast.drivers_notified` (gauge)
- `ride.matching.timeout.rate` (counter)

### 10.3 Logging Strategy

**Log Levels:**

- **`INFO`**: Key state transitions (session created, driver accepted, broadcast started)
- **`DEBUG`**: Redis operations, message routing details
- **`WARN`**: Recoverable errors (driver not found, stale location update)
- **`ERROR`**: Unrecoverable errors (deserialization failure, DB constraint violation)

**Key Log Lines:**

```java
// Session lifecycle
log.info("Initialized session for request {} - saving to Redis", requestId);
log.info("Entered broadcast mode for request {} - {} drivers notified", 
    requestId, candidates.size());
log.info("Session completed for request {} - driver {} accepted", 
    requestId, driverId);

// Idempotency
log.debug("Skipping duplicate message {} for request {}", 
    correlationId, requestId);

// Errors
log.error("Failed to process matching command {} for request {}", 
    commandType, requestId, exception);
```

**Structured Logging:**
Use MDC (Mapped Diagnostic Context) to add `requestId` to all logs for a given processing flow:

```java
MDC.put("requestId", String.valueOf(requestId));
try {
    // ... processing logic
} finally {
    MDC.remove("requestId");
}
```

---

## 11. Comparison: Legacy vs. Queue-Based

### 11.1 Architecture Comparison

| Aspect | Legacy (In-Memory) | Queue-Based (Current) |
|--------|-------------------|----------------------|
| **State Storage** | `ConcurrentHashMap` in JVM | Redis (shared across instances) |
| **Scalability** | Single node only | Horizontal (any instance can process) |
| **Crash Recovery** | Lost (all sessions gone) | Resilient (Redis + MQ persist state) |
| **Timers** | `ScheduledExecutorService` (local) | RabbitMQ delay queues (distributed) |
| **Coordination** | In-process method calls | Asynchronous messages |
| **Testing** | Requires mock executors | Can test with embedded RabbitMQ |
| **Observability** | Thread dumps, heap analysis | Message tracing, queue depths |
| **Complexity** | Lower (simpler code) | Higher (distributed system concepts) |

### 11.2 When to Use Each

**Use Legacy Coordinator if:**
- Single-node deployment acceptable
- Simplicity preferred over scalability
- RabbitMQ/Redis infrastructure unavailable
- Development/testing environments

**Use Queue-Based System if:**
- Production environment with scaling needs
- Need crash recovery and high availability
- Want to isolate real-time processing from API layer
- Building multi-region or microservices architecture

**Switching Between Modes:**

```yaml
# Legacy mode
app.messaging.ride.enabled=false
app.messaging.ride.matching-enabled=false

# Queue mode
app.messaging.ride.enabled=true
app.messaging.ride.matching-enabled=true
```

No code changes required—both systems share the same REST API contracts and domain services.

---

## 12. Common Scenarios & Troubleshooting

### 12.1 Scenario: Driver Accepts But Rider Doesn't Get Notification

**Symptoms:**
- Driver sees "Acceptance successful"
- Rider app shows "Searching for driver..." indefinitely
- Database shows request as `CONFIRMED`

**Diagnosis Steps:**

1. **Check logs for orchestrator processing:**
   ```
   grep "Processing driver response for request" application.log
   ```
   If missing → orchestrator didn't receive `DRIVER_RESPONSE` command

2. **Check if notification published:**
   ```
   grep "Published rider status notification to MQ" application.log
   ```
   If missing → orchestrator logic error

3. **Check if notification consumed:**
   ```
   grep "Received rider status notification from MQ" application.log
   ```
   If missing → notification listener not consuming

4. **Check WebSocket connection:**
   - Verify rider subscribed to `/user/{userId}/queue/ride-matching`
   - Check WebSocket session active in STOMP broker

**Common Fixes:**
- Restart app (reconnects RabbitMQ listeners)
- Check `app.messaging.ride.notifications-enabled=true`
- Verify notification queue binding exists in RabbitMQ UI

### 12.2 Scenario: Session Not Found in Redis

**Symptoms:**
```
WARN - No session found in Redis for request 42 (key: ride:matching:session:42)
```

**Possible Causes:**

1. **Session expired (TTL elapsed):**
   - Check request creation time
   - If > 15 minutes → expected behavior
   - Solution: Adjust `matching-request-timeout` if needed

2. **Redis connection lost:**
   - Check health endpoint: `/actuator/health/rideMatching`
   - Check Redis server logs
   - Solution: Restart Redis or app to reconnect

3. **Session never created:**
   - Check logs for "Initialized session for request 42"
   - If missing → orchestrator didn't process `RideRequestCreated` event
   - Solution: Check if event published and queue bindings correct

4. **Serialization error:**
   - Check for errors during `sessionRepository.save()`
   - Solution: Review Redis serialization config

### 12.3 Scenario: Broadcast Phase Never Triggers

**Symptoms:**
- All sequential drivers timeout/reject
- Request expires without entering broadcast
- No "Entered broadcast mode" log

**Diagnosis:**

1. **Check eligible drivers query:**
   ```sql
   SELECT * FROM driver_profiles 
   WHERE status = 'ACTIVE' 
   AND driver_id NOT IN (...)
   AND NOT EXISTS (
       SELECT 1 FROM shared_rides 
       WHERE driver_id = driver_profiles.driver_id 
       AND status = 'ONGOING'
   );
   ```
   If returns 0 → no eligible drivers

2. **Check remaining time calculation:**
   ```java
   Duration remaining = Duration.between(Instant.now(), 
                                         state.getRequestDeadline());
   log.info("Remaining time for broadcast: {} seconds", 
            remaining.getSeconds());
   ```
   If ≤ 0 → already expired

**Common Fixes:**
- Ensure drivers set status to `ACTIVE` (not `INACTIVE` or `BUSY`)
- Increase `request-acceptance-timeout` to allow more time for broadcast
- Add test drivers to database for development

### 12.4 Scenario: High DLQ Depth

**Symptoms:**
- DLQ accumulating messages
- Matching delays increasing
- Errors in logs

**Investigation:**

1. **Inspect DLQ messages in RabbitMQ UI:**
   - Note message body and error headers
   - Look for patterns (all same request? all same command type?)

2. **Common causes:**
   - Database constraint violations (e.g., duplicate key)
   - Missing entity references (driver/ride deleted mid-matching)
   - Serialization issues (incompatible class versions after deploy)

3. **Resolution:**
   - Fix root cause (update code, clean DB data)
   - Purge DLQ if messages no longer valid
   - Re-publish corrected messages if needed

### 12.5 Scenario: JOIN Request Entering Broadcast Mode (FIXED)

**Symptoms (before fix):**
- JOIN_RIDE requests unexpectedly entering `BROADCASTING` phase
- Rider receives multiple driver offers for a direct join request
- Wallet hold not released immediately on driver timeout

**Root Cause:**
The orchestrator was treating all request types the same way. When a JOIN request's driver timed out, it would:
1. Try to send the next offer (but JOIN requests have no proposals)
2. Call `handleNoMoreCandidates` → `tryEnterBroadcast`
3. Enter broadcast mode and notify all eligible drivers

**This was incorrect because:**
- JOIN requests target a **specific driver/ride combination**
- If that driver rejects/times out, the request should **immediately fail**
- Broadcast mode only makes sense for BOOKING requests (AI matching)

**Fix Applied:**

1. **Added `requestKind` field to `MatchingSessionState`:**
   ```java
   private RequestKind requestKind; // BOOKING or JOIN_RIDE
   ```

2. **Set `requestKind` when creating sessions:**
   - BOOKING: `requestKind = RequestKind.BOOKING`
   - JOIN_RIDE: `requestKind = RequestKind.JOIN_RIDE`

3. **Modified `handleDriverTimeout` to check request type:**
   ```java
   if (state.isJoinRequest()) {
       // Immediately fail - no retry, no broadcast
       state.markExpired();
       dispatchRiderStatus(request, 
           responseAssembler.toRiderJoinRequestFailed(
               request, "Driver did not respond in time"));
       return;
   }
   // BOOKING requests continue to next candidate
   ```

4. **Modified `handleNoMoreCandidates` to block broadcast for JOIN:**
   ```java
   if (state.isJoinRequest()) {
       // Never enter broadcast for JOIN requests
       state.markExpired();
       dispatchRiderStatus(request, 
           responseAssembler.toRiderJoinRequestFailed(...));
       return;
   }
   ```

5. **Added safety check in `tryEnterBroadcast`:**
   ```java
   if (state.isJoinRequest()) {
       log.warn("Attempted to enter broadcast mode for JOIN_RIDE - should never happen");
       return false;
   }
   ```

**Verification:**
```bash
# Check session requestKind in Redis
redis-cli GET "ride:matching:session:42"
# Should show: "requestKind":"JOIN_RIDE"

# Check logs for JOIN timeout
grep "JOIN_RIDE request .* timed out" application.log
# Should show: "Marking as expired" (not "entering broadcast")
```

**Impact:**
- ✅ JOIN requests fail fast on driver timeout
- ✅ Wallet holds released immediately
- ✅ No unexpected broadcast notifications to other drivers
- ✅ Clearer rider feedback (timeout vs. no drivers available)

---

## 13. Future Enhancements

### 13.1 Planned Features

**Phase 4: Advanced Location Services**
- Real-time ETA recalculation based on driver location updates
- Proximity alerts when driver approaches pickup
- Off-route detection and rider notifications
- Dynamic pricing based on demand heatmaps

**Phase 5: Multi-Region Support**
- Redis cluster with geo-replication
- RabbitMQ federation across regions
- Locality-aware matching (prefer drivers in same region)

**Phase 6: ML-Enhanced Matching**
- Predictive driver acceptance scoring
- Traffic pattern learning for better ETA
- Dynamic timeout adjustment based on historical data

### 13.2 Performance Optimizations

**Current Bottlenecks:**
- Redis round-trips for every state read/write
- Sequential processing of commands (concurrency=1)
- Full proposal list stored in session (memory overhead for 100+ candidates)

**Optimization Ideas:**
1. **Redis pipeline/batch operations**
   - Group multiple Redis commands into single network call
   - Reduces latency for high-throughput scenarios

2. **Session state compression**
   - Store only proposal IDs, fetch full data on-demand
   - Use Redis ZSET for ranked proposals (score-based retrieval)

3. **Parallel command processing**
   - Partition commands by request ID hash
   - Multiple consumers on command queue with sharding

4. **Caching layer**
   - In-memory cache for driver profiles (refresh every 5 min)
   - Reduces DB queries during matching

---

## 14. API Integration Guide (Mobile Clients)

### 14.1 No Changes Required

**Good news:** The queue-based system is **transparent to mobile clients**. All REST API contracts remain unchanged:

- `POST /api/v1/ride-requests` → Create booking (same request/response)
- `POST /api/v1/shared-rides/{id}/join` → Join ride (same flow)
- `POST /api/v1/ride-requests/{id}/accept` → Driver acceptance (same validation)
- WebSocket topics unchanged (`/user/{id}/queue/ride-matching`, `/user/{id}/queue/ride-offers`)

### 14.2 WebSocket Subscription (Reminder)

**Rider App:**
```javascript
const stompClient = Stomp.over(new SockJS('/ws'));
stompClient.connect({}, () => {
    stompClient.subscribe(`/user/${userId}/queue/ride-matching`, (message) => {
        const notification = JSON.parse(message.body);
        if (notification.status === 'ACCEPTED') {
            // Show "Driver found!" UI
            // Display driver details, vehicle, ETA
        } else if (notification.status === 'NO_MATCH') {
            // Show "No drivers available" message
        }
    });
});
```

**Driver App:**
```javascript
stompClient.subscribe(`/user/${userId}/queue/ride-offers`, (message) => {
    const offer = JSON.parse(message.body);
    if (offer.broadcast) {
        // Show broadcast offer with countdown
        // Multiple drivers may see this simultaneously
    } else {
        // Show exclusive offer with ranking (e.g., "You're offer 2 of 5")
    }
    startCountdown(offer.offerExpiresAt);
});
```

### 14.3 Error Handling

**Existing error responses still apply:**

| HTTP Status | Scenario | Action |
|-------------|----------|--------|
| `503 Service Unavailable` | RabbitMQ or Redis down | Show "Service temporarily unavailable" |
| `409 Conflict` | Offer already expired/claimed | Show "Offer no longer available" |
| `400 Bad Request` | Invalid request data | Show validation errors |

---

## 15. Testing Strategy

### 15.1 Unit Tests

**Focus:** Individual component logic without external dependencies

```java
@Test
void shouldMarkSessionCompleted() {
    MatchingSessionState session = MatchingSessionState.initialize(...);
    session.markCompleted();
    
    assertThat(session.getPhase()).isEqualTo(COMPLETED);
    assertThat(session.getActiveOffer()).isNull();
    assertThat(session.isTerminal()).isTrue();
}
```

### 15.2 Integration Tests

**Focus:** Message flow with embedded RabbitMQ and Redis

```java
@SpringBootTest
@TestConfiguration
class QueueRideMatchingIntegrationTest {
    
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired MatchingSessionRepository sessionRepository;
    
    @Test
    void shouldCompleteBookingFlowEndToEnd() {
        // Given: ride request created
        SharedRideRequest request = createTestRequest();
        
        // When: publish ride request created event
        rabbitTemplate.convertAndSend(
            "ride.events",
            "ride.request.created",
            RideRequestCreatedMessage.from(request.getId()));
        
        // Then: session created in Redis
        await().atMost(5, SECONDS).until(() -> 
            sessionRepository.find(request.getId()).isPresent());
        
        // And: driver offer sent
        verify(notificationService).notifyDriverOffer(...);
    }
}
```

### 15.3 Load Testing

**Scenario:** 100 concurrent booking requests

**Tools:** JMeter or Gatling

**Metrics to track:**
- Request creation latency (p50, p95, p99)
- Time to first driver offer
- Redis memory usage
- RabbitMQ queue depth
- Database connection pool saturation

**Expected Results:**
- < 200ms API response time for request creation
- < 5s to first driver offer
- Redis memory < 500 MB for 1000 active sessions
- No message backlog in queues

---

## 16. Deployment Checklist

### 16.1 Pre-Deployment

- [ ] Verify RabbitMQ cluster healthy and queues declared
- [ ] Verify Redis cluster healthy with sufficient memory
- [ ] Review configuration properties for environment
- [ ] Run integration tests against staging environment
- [ ] Set up monitoring dashboards (Grafana/Prometheus)
- [ ] Configure alerts for DLQ depth, Redis memory, RabbitMQ connections

### 16.2 Deployment Steps

1. **Deploy with MQ disabled (feature flag off):**
   ```yaml
   app.messaging.ride.matching-enabled=false
   ```
   Verify legacy coordinator still working

2. **Enable MQ for read-only (publish events but don't process):**
   ```yaml
   app.messaging.ride.enabled=true
   app.messaging.ride.matching-enabled=false
   ```
   Verify events publishing to RabbitMQ

3. **Enable queue orchestrator for subset of traffic:**
   Use A/B testing or canary deployment
   Monitor error rates and latency

4. **Full rollout:**
   ```yaml
   app.messaging.ride.matching-enabled=true
   app.messaging.ride.notifications-enabled=true
   ```
   Monitor for 24 hours before declaring success

### 16.3 Rollback Plan

**If issues detected:**

1. **Immediate:** Set feature flag to disable MQ
   ```yaml
   app.messaging.ride.matching-enabled=false
   ```

2. **Graceful:** Let in-progress sessions complete
   - Monitor Redis session count (`/actuator/health/rideMatching`)
   - Wait for sessions to expire (max 15 minutes)
   - Then disable MQ completely

3. **Investigate:** Check logs, DLQ, Redis state

4. **Fix and retry:** Address root cause, redeploy with fix

---

## 17. Operational Runbook

### 17.1 Daily Operations

**Morning Check:**
- [ ] Review `/actuator/health` endpoint (all green?)
- [ ] Check RabbitMQ queue depths (any backlog?)
- [ ] Review DLQ (any messages?)
- [ ] Check Redis memory usage (< 80%?)

**Incident Response:**

**Alert: "High DLQ Depth"**
1. Check DLQ messages in RabbitMQ UI
2. Identify pattern (common error, specific request?)
3. Fix root cause (code bug, data issue)
4. Purge DLQ or re-publish after fix

**Alert: "Redis Memory High"**
1. Check session count (`/actuator/health`)
2. Review TTL settings (too long?)
3. Flush expired keys: `redis-cli --scan --pattern "ride:matching:session:*" | xargs redis-cli DEL`
4. Consider Redis memory upgrade

**Alert: "RabbitMQ Connection Errors"**
1. Check RabbitMQ server status
2. Restart app to force reconnect
3. Review connection limits and credentials

### 17.2 Scaling Guidelines

**When to scale:**
- API latency > 500ms (p95)
- RabbitMQ queue depth growing consistently
- CPU usage > 70% sustained
- Redis memory > 80%

**Horizontal Scaling:**
- Add more app instances (stateless design)
- RabbitMQ auto-distributes messages
- No changes to Redis (shared state)

**Vertical Scaling:**
- Increase Redis memory if session count high
- Increase RabbitMQ resources if message throughput bottleneck

---

## 18. Glossary

| Term | Definition |
|------|------------|
| **Orchestrator** | Component that consumes messages and coordinates matching workflow |
| **Session State** | Redis-stored metadata about a matching attempt (proposals, phase, active offer) |
| **Active Offer** | Current driver/ride pair waiting for response (tracked in session state) |
| **Sequential Matching** | One-by-one driver offers based on ranked proposals |
| **Broadcast Matching** | Simultaneous offers to all eligible drivers (fallback mode) |
| **Correlation ID** | Unique message identifier for idempotency and tracing |
| **TTL (Time To Live)** | Redis key expiration duration; also RabbitMQ message expiration |
| **DLQ (Dead Letter Queue)** | Destination for messages that failed processing after retries |
| **Delay Queue** | RabbitMQ queue with TTL used for scheduling future events (e.g., timeouts) |
| **Idempotency** | Property ensuring repeated message processing produces same result |
| **Pub/Sub** | Publish-Subscribe pattern where messages broadcast to multiple consumers |
| **Command** | Message instructing an action (imperative: "send offer") |
| **Event** | Message describing a fact (declarative: "request created") |
| **Phase** | State within the matching session lifecycle (MATCHING, BROADCASTING, etc.) |

---

## 19. Quick Reference

### 19.1 Key Files

| File | Purpose |
|------|---------|
| `QueueRideMatchingOrchestrator.java` | Main orchestrator consuming RabbitMQ messages |
| `MatchingSessionState.java` | Redis-serializable session data model |
| `RedisMatchingSessionRepository.java` | Redis persistence layer |
| `RideMatchingCommandPublisher.java` | Publishes commands to RabbitMQ |
| `RideNotificationEventPublisher.java` | Publishes notifications to RabbitMQ |
| `RideNotificationEventListener.java` | Consumes notifications and dispatches via WebSocket |
| `RideMessagingProperties.java` | Configuration properties for queues, timeouts, flags |
| `RedisConfig.java` | Redis connection and serialization setup |
| `ApplicationEventPublisherService.java` | Facade for publishing ride events |

### 19.2 Key Properties

```yaml
# Feature flags
app.messaging.ride.enabled=true
app.messaging.ride.matching-enabled=true
app.messaging.ride.notifications-enabled=true

# Timeouts
app.ride.request-acceptance-timeout=PT15M
app.messaging.ride.driver-response-window=PT90S
app.ride.broadcast.response-window-seconds=30

# RabbitMQ
spring.rabbitmq.host=chameleon.lmq.cloudamqp.com
spring.rabbitmq.port=5671
spring.rabbitmq.ssl.enabled=true

# Redis
spring.data.redis.url=rediss://host:port
spring.data.redis.ssl.enabled=true
```

### 19.3 Useful Commands

**Check Redis sessions:**
```bash
redis-cli --scan --pattern "ride:matching:session:*"
redis-cli GET "ride:matching:session:42"
```

**Purge RabbitMQ queue:**
```bash
rabbitmqadmin purge queue name=ride.matching.dlq
```

**Monitor queue depth:**
```bash
rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

**Check app health:**
```bash
curl http://localhost:8080/actuator/health/rideMatching
```

---

## 20. Conclusion

This queue-based ride processing system represents a significant architectural evolution, moving from a monolithic, in-memory coordinator to a distributed, resilient, and scalable message-driven design. While it introduces additional complexity (RabbitMQ, Redis, distributed state), the benefits—horizontal scalability, crash recovery, clear separation of concerns—make it suitable for production environments and future growth.

The system has been designed with **backwards compatibility** and **gradual rollout** in mind, allowing teams to adopt it incrementally and fall back to the legacy coordinator if needed. Feature flags, health indicators, and comprehensive logging provide the operational visibility needed to run this system confidently in production.

For questions, clarifications, or contributions, refer to the codebase documentation, raise issues with the development team, or consult this guide's reference sections.

**Maintained by:** Backend Team  
**Last Review:** November 2025  
**Next Review:** After Phase 4 deployment (Location Services)







