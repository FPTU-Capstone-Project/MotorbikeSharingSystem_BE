# Phân Tích Tổng Hợp Nghiệp Vụ Transaction & Wallet

## Mục Lục

1. [Tổng Quan Hệ Thống](#1-tổng-quan-hệ-thống)
2. [Kiến Trúc SSOT (Single Source of Truth)](#2-kiến-trúc-ssot-single-source-of-truth)
3. [Wallet Entity & Balance Calculation](#3-wallet-entity--balance-calculation)
4. [Transaction Types & Flows](#4-transaction-types--flows)
5. [Các Nghiệp Vụ Chính](#5-các-nghiệp-vụ-chính)
6. [Business Rules & Constraints](#6-business-rules--constraints)
7. [Flow Diagrams](#7-flow-diagrams)
8. [Edge Cases & Error Handling](#8-edge-cases--error-handling)
9. [Best Practices](#9-best-practices)

---

## 1. Tổng Quan Hệ Thống

### 1.1. Mục Đích

Hệ thống ví điện tử (Wallet) và giao dịch (Transaction) được thiết kế để:
- Quản lý số dư của người dùng (rider/driver)
- Xử lý các giao dịch tài chính (top-up, payout, hold, capture)
- Đảm bảo tính nhất quán và toàn vẹn dữ liệu (SSOT)
- Tuân thủ nguyên tắc kế toán kép (Double-Entry Accounting)
- Hỗ trợ reconciliation và audit trail

### 1.2. Kiến Trúc Tổng Quan

```
┌─────────────────────────────────────────────────────────────┐
│                    USER INTERFACE                           │
│  (REST API Controllers)                                      │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              SERVICE LAYER                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ WalletService│  │Transaction   │  │BalanceCalc  │     │
│  │              │  │Service       │  │Service       │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              REPOSITORY LAYER                                │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │WalletRepo    │  │Transaction   │                        │
│  │              │  │Repository    │                        │
│  └──────────────┘  └──────────────┘                        │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│              DATABASE LAYER                                  │
│  ┌──────────────┐  ┌──────────────┐                        │
│  │wallets       │  │transactions  │  (SSOT - Ledger)       │
│  │(metadata)    │  │(immutable)   │                        │
│  └──────────────┘  └──────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Kiến Trúc SSOT (Single Source of Truth)

### 2.1. Nguyên Tắc SSOT

**Single Source of Truth (SSOT)** là nguyên tắc thiết kế quan trọng nhất:

- ✅ **Balance KHÔNG được lưu trực tiếp trong `Wallet` entity**
- ✅ **Balance được tính từ `Transaction` ledger (immutable)**
- ✅ **`Wallet` entity chỉ chứa metadata** (totalToppedUp, totalSpent, isActive)
- ✅ **Mọi thay đổi balance phải thông qua Transaction entries**

### 2.2. Lợi Ích

1. **Tính Nhất Quán**: Balance luôn đúng vì được tính từ ledger
2. **Audit Trail**: Mọi thay đổi đều có transaction record
3. **Không Mất Dữ Liệu**: Ledger là immutable, không thể sửa/xóa
4. **Reconciliation**: Dễ dàng so khớp và kiểm tra
5. **Scalability**: Có thể cache balance mà không lo inconsistency

### 2.3. Balance Calculation Flow

```
User Request Balance
        │
        ▼
BalanceCalculationService.calculateAvailableBalance()
        │
        ▼
TransactionRepository.calculateAvailableBalance()
        │
        ▼
SQL Query: SUM(transactions WHERE status='SUCCESS')
        │
        ▼
Return Balance (cached in Redis)
```

---

## 3. Wallet Entity & Balance Calculation

### 3.1. Wallet Entity Structure

```java
@Entity
public class Wallet {
    Integer walletId;           // Primary key
    User user;                 // One-to-one relationship
    BigDecimal totalToppedUp;  // Metadata: Tổng đã nạp
    BigDecimal totalSpent;     // Metadata: Tổng đã chi
    Boolean isActive;          // Trạng thái ví
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Long version;              // Optimistic locking
}
```

**Lưu ý:**
- ❌ **KHÔNG có** `shadowBalance` hoặc `pendingBalance` fields
- ✅ Balance được tính từ `Transaction` ledger

### 3.2. Balance Types

#### 3.2.1. Available Balance (Số Dư Khả Dụng)

**Định nghĩa:**
- Số tiền user có thể sử dụng ngay
- Được tính từ các transactions SUCCESS

**Công thức:**
```sql
Available = SUM(
    + TOPUP IN
    + REFUND IN
    + CAPTURE_FARE IN (driver)
    - PAYOUT OUT
    - HOLD_CREATE (trừ vào available)
    + HOLD_RELEASE (cộng vào available)
)
WHERE status = 'SUCCESS'
```

**Query Implementation:**
```java
@Query("""
    SELECT COALESCE(SUM(
        CASE 
            WHEN direction = 'IN' AND type IN ('TOPUP', 'REFUND', 'CAPTURE_FARE') THEN amount
            WHEN direction = 'OUT' AND type = 'PAYOUT' THEN -amount
            WHEN direction = 'INTERNAL' AND type = 'HOLD_CREATE' THEN -amount
            WHEN direction = 'INTERNAL' AND type = 'HOLD_RELEASE' THEN amount
            ELSE 0
        END
    ), 0)
    FROM transactions
    WHERE wallet_id = :walletId AND status = 'SUCCESS'
""")
BigDecimal calculateAvailableBalance(Integer walletId);
```

#### 3.2.2. Pending Balance (Số Dư Đang Hold)

**Định nghĩa:**
- Số tiền đang bị hold (chưa capture)
- Được tính từ HOLD_CREATE và HOLD_RELEASE

**Công thức:**
```sql
Pending = SUM(
    + HOLD_CREATE
    - HOLD_RELEASE
    - CAPTURE_FARE OUT (khi capture, pending giảm)
)
WHERE status = 'SUCCESS'
```

**Query Implementation:**
```java
@Query("""
    SELECT COALESCE(SUM(
        CASE 
            WHEN type = 'HOLD_CREATE' THEN amount
            WHEN type = 'HOLD_RELEASE' THEN -amount
            WHEN type = 'CAPTURE_FARE' AND direction = 'OUT' THEN -amount
            ELSE 0
        END
    ), 0)
    FROM transactions
    WHERE wallet_id = :walletId AND status = 'SUCCESS'
""")
BigDecimal calculatePendingBalance(Integer walletId);
```

#### 3.2.3. Total Balance (Tổng Số Dư)

**Định nghĩa:**
- Tổng số dư = Available + Pending

**Công thức:**
```java
Total = calculateAvailableBalance(walletId) + calculatePendingBalance(walletId)
```

### 3.3. Balance Caching Strategy

**Redis Caching:**
- ✅ Cache available, pending, total balance
- ✅ TTL: 5 phút
- ✅ Invalidate khi có transaction mới
- ✅ Key format: `walletBalance:available:{walletId}`

**Cache Invalidation Points:**
- Sau khi tạo transaction mới
- Sau khi update transaction status
- Sau khi hold/release
- Sau khi capture fare

---

## 4. Transaction Types & Flows

### 4.1. Transaction Entity Structure

```java
@Entity
public class Transaction {
    Integer txnId;              // Primary key
    UUID groupId;              // Group related transactions
    TransactionType type;       // TOPUP, HOLD_CREATE, etc.
    TransactionDirection direction;  // IN, OUT, INTERNAL
    ActorKind actorKind;       // USER, SYSTEM
    User actorUser;            // User who initiated
    Wallet wallet;             // Wallet relationship (nullable for SYSTEM)
    SystemWallet systemWallet; // MASTER, COMMISSION, PROMO
    BigDecimal amount;         // Transaction amount
    String currency;           // VND
    TransactionStatus status;  // PENDING, SUCCESS, FAILED, REVERSED
    String pspRef;            // PSP reference (unique)
    String idempotencyKey;    // Idempotency key (unique)
    UUID groupId;             // Group related transactions
    BigDecimal beforeAvail;    // Snapshot before
    BigDecimal afterAvail;    // Snapshot after
    BigDecimal beforePending;  // Snapshot before
    BigDecimal afterPending;   // Snapshot after
    String note;              // Description
    LocalDateTime createdAt;   // Immutable
}
```

### 4.2. Transaction Types

#### 4.2.1. TOPUP

**Mục đích:** Nạp tiền vào ví từ Payment Service Provider (PSP)

**Flow:**
```
User Request Top-up
        │
        ▼
initTopup() → Create 2 transactions (PENDING):
    - System.MASTER OUT (system gives money to user)
    - User IN (user receives money)
        │
        ▼
PSP Payment Link → User pays
        │
        ▼
Webhook SUCCESS → handleTopupSuccess()
    - Update both transactions → SUCCESS
    - Balance increases
        │
        ▼
Webhook FAILED → handleTopupFailed()
    - Update both transactions → FAILED
    - Balance unchanged
```

**Double-Entry:**
- System.MASTER OUT (Debit)
- User IN (Credit)
- **Balanced:** System OUT = User IN

**Idempotency:**
- Key: `"TOPUP_" + pspRef + "_" + amount`
- Check: `findByIdempotencyKey()`

**Balance Impact:**
- Available: +amount (khi SUCCESS)
- Pending: 0

---

#### 4.2.2. HOLD_CREATE

**Mục đích:** Hold tiền khi rider book ride

**Flow:**
```
Rider Books Ride
        │
        ▼
holdAmount() → Create HOLD_CREATE transaction (SUCCESS):
    - Check available >= amount
    - Lock wallet (pessimistic lock)
    - Create HOLD_CREATE (INTERNAL)
        │
        ▼
Balance Impact:
    - Available: -amount
    - Pending: +amount
```

**Double-Entry:**
- HOLD_CREATE là INTERNAL transaction (không cần mirror)
- Chỉ ảnh hưởng available và pending balance

**Idempotency:**
- Key: `"HOLD_" + groupId.toString()`
- Check: `findByIdempotencyKey()`

**Balance Impact:**
- Available: -amount
- Pending: +amount

---

#### 4.2.3. HOLD_RELEASE

**Mục đích:** Release hold khi ride bị cancel

**Flow:**
```
Ride Cancelled
        │
        ▼
releaseHold() → Create HOLD_RELEASE transaction (SUCCESS):
    - Find HOLD_CREATE by groupId
    - Check if already released
    - Create HOLD_RELEASE (INTERNAL)
        │
        ▼
Balance Impact:
    - Available: +amount
    - Pending: -amount
```

**Idempotency:**
- Check: `findByGroupIdAndType(groupId, HOLD_RELEASE)`
- Nếu đã release → return existing

**Balance Impact:**
- Available: +amount
- Pending: -amount

---

#### 4.2.4. CAPTURE_FARE

**Mục đích:** Capture tiền khi ride hoàn thành

**Flow:**
```
Ride Completed
        │
        ▼
settleRideFunds() → Create 3 transactions (SUCCESS):
    - Rider OUT (payment)
    - Driver IN (payout)
    - System.COMMISSION IN (commission)
        │
        ▼
Balance Impact:
    - Rider: Pending -amount (release hold)
    - Driver: Available +payoutAmount
    - System: Commission +commissionAmount
```

**Triple-Entry Balancing:**
- Rider OUT = Driver IN + Commission IN
- **Validation:** `riderPayAmount == driverPayoutAmount + commissionAmount`

**Idempotency:**
- Key: `"CAPTURE_FARE_" + rideRequestId + "_" + groupId`
- Check: `findByIdempotencyKey()`

**Balance Impact:**
- Rider Available: 0 (không đổi)
- Rider Pending: -amount (release hold)
- Driver Available: +payoutAmount
- System Commission: +commissionAmount

**Lưu ý:**
- ✅ CAPTURE_FARE OUT không trừ available (chỉ trừ pending)
- ✅ CAPTURE_FARE IN cộng vào available

---

#### 4.2.5. PAYOUT

**Mục đích:** Rút tiền từ ví ra tài khoản ngân hàng

**Flow:**
```
Driver Request Payout
        │
        ▼
initPayout() → Create 2 transactions (PENDING):
    - User OUT (user sends money)
    - System.MASTER OUT (system sends money to bank)
        │
        ▼
PSP Processing
        │
        ▼
Webhook SUCCESS → handlePayoutSuccess()
    - Update both transactions → SUCCESS
    - Balance decreases
        │
        ▼
Webhook FAILED → handlePayoutFailed()
    - Update both transactions → REVERSED
    - Create REFUND transactions
    - Balance restored
```

**Double-Entry:**
- User OUT (Debit)
- System.MASTER OUT (Debit)
- **Balanced:** Cả 2 đều OUT (money leaves system)

**Idempotency:**
- Key: `"PAYOUT_" + pspRef + "_" + amount`
- Check: `findByIdempotencyKey()`

**Balance Impact:**
- Available: -amount (khi SUCCESS)
- Pending: 0

---

#### 4.2.6. REFUND

**Mục đích:** Hoàn tiền cho user

**Các Trường Hợp:**

1. **Refund Ride:**
   - Rider IN (refund credit)
   - Driver OUT (refund debit)
   - **Balanced:** Rider IN = Driver OUT

2. **Refund Topup:**
   - User OUT (refund debit - reversal của User IN)
   - System.MASTER IN (refund credit - reversal của System.MASTER OUT)
   - **Balanced:** User OUT + System IN = 0

3. **Refund Payout (Failed):**
   - Driver IN (refund credit - reversal của Driver OUT)
   - System.MASTER IN (refund credit - reversal của System.MASTER OUT)
   - **Balanced:** Driver IN + System IN = 0 (cả 2 đều reversal của OUT)

**Idempotency:**
- Key: `"REFUND_" + type + "_" + originalGroupId + "_" + amount`
- Check: `findByIdempotencyKey()`

**Reversal Entries:**
- Mark original transactions as `REVERSED`
- Create new REFUND transactions với opposite direction

---

#### 4.2.7. PROMO_CREDIT

**Mục đích:** Tặng tiền khuyến mãi cho user

**Flow:**
```
Admin Grants Promo Credit
        │
        ▼
Create 2 transactions (SUCCESS):
    - System.PROMO OUT
    - User IN
```

**Double-Entry:**
- System.PROMO OUT (Debit)
- User IN (Credit)
- **Balanced:** System OUT = User IN

---

#### 4.2.8. ADJUSTMENT

**Mục đích:** Điều chỉnh số dư (correction, compensation)

**Flow:**
```
Admin Creates Adjustment
        │
        ▼
Create adjustment transaction (SUCCESS):
    - User IN/OUT (depending on adjustment type)
```

**Lưu ý:**
- Có thể là IN hoặc OUT
- Thường có note giải thích lý do

---

## 5. Các Nghiệp Vụ Chính

### 5.1. Top-Up Flow

**Actors:** User, PSP (PayOS/VNPay), System

**Steps:**
1. User request top-up → `initTopup()`
2. Create PENDING transactions (System OUT + User IN)
3. Generate payment link → User pays
4. PSP webhook → `handleTopupSuccess()` hoặc `handleTopupFailed()`
5. Update transactions status
6. Balance changes (nếu SUCCESS)

**Business Rules:**
- ✅ Idempotency: Check `idempotencyKey` trước khi tạo
- ✅ Webhook idempotency: Check `pspRef + SUCCESS` trước khi update
- ✅ Double-entry: System OUT = User IN
- ✅ Status flow: PENDING → SUCCESS/FAILED

**Error Handling:**
- Webhook retry: Idempotency check prevents duplicate
- PSP failure: Mark FAILED, không ảnh hưởng balance

---

### 5.2. Hold & Release Flow

**Actors:** Rider, System

**Steps:**
1. Rider books ride → `holdAmount()`
2. Check available balance >= amount
3. Lock wallet (pessimistic lock)
4. Create HOLD_CREATE transaction
5. Balance: Available ↓, Pending ↑
6. Ride cancelled → `releaseHold()`
7. Create HOLD_RELEASE transaction
8. Balance: Available ↑, Pending ↓

**Business Rules:**
- ✅ Lock wallet trước khi check balance
- ✅ Idempotency: Check `idempotencyKey` trước khi tạo
- ✅ Release idempotency: Check `findByGroupIdAndType(HOLD_RELEASE)`
- ✅ Orphaned hold cleanup: Daily scheduler releases holds > 7 days

**Error Handling:**
- Insufficient balance: Throw `ValidationException`
- Duplicate hold: Return existing transaction
- Orphaned hold: Auto-release after 7 days

---

### 5.3. Capture Fare Flow

**Actors:** Rider, Driver, System

**Steps:**
1. Ride completed → `settleRideFunds()`
2. Lock both rider and driver wallets
3. Check idempotency (prevent duplicate capture)
4. Validate triple-entry balancing
5. Create 3 transactions:
   - Rider OUT
   - Driver IN
   - System.COMMISSION IN
6. Balance impact:
   - Rider: Pending ↓
   - Driver: Available ↑
   - System: Commission ↑

**Business Rules:**
- ✅ Triple-entry: Rider OUT = Driver IN + Commission IN
- ✅ Idempotency: Check `idempotencyKey` trước khi tạo
- ✅ Lock wallets: Pessimistic lock cho cả rider và driver
- ✅ CAPTURE_FARE OUT không trừ available (chỉ trừ pending)

**Error Handling:**
- Balancing violation: Throw `ValidationException`
- Duplicate capture: Return existing transactions
- Insufficient pending: Should not happen (already held)

---

### 5.4. Payout Flow

**Actors:** Driver, PSP, System

**Steps:**
1. Driver request payout → `initPayout()`
2. Lock wallet
3. Check available balance >= amount
4. Create PENDING transactions (User OUT + System OUT)
5. PSP processing
6. Webhook SUCCESS → `handlePayoutSuccess()`
   - Update transactions → SUCCESS
   - Balance decreases
7. Webhook FAILED → `handlePayoutFailed()`
   - Update transactions → REVERSED
   - Create REFUND transactions
   - Balance restored

**Business Rules:**
- ✅ Lock wallet trước khi check balance
- ✅ Idempotency: Check `idempotencyKey` trước khi tạo
- ✅ Webhook idempotency: Check `pspRef + SUCCESS` trước khi update
- ✅ Double-entry: User OUT + System OUT (both OUT)
- ✅ Failed payout: Auto-refund

**Error Handling:**
- Insufficient balance: Throw `ValidationException`
- PSP failure: Auto-refund, restore balance
- Webhook retry: Idempotency prevents duplicate

---

### 5.5. Refund Flow

**Actors:** User, System

**Các Trường Hợp:**

1. **Ride Refund:**
   - `refundRide()` → Create 2 transactions
   - Rider IN, Driver OUT
   - Mark original CAPTURE_FARE as REVERSED

2. **Topup Refund:**
   - `refundTopup()` → Create 2 transactions
   - User OUT (reversal), System.MASTER IN (reversal)
   - Mark original TOPUP as REVERSED

3. **Payout Refund (Failed):**
   - `handlePayoutFailed()` → Auto-refund
   - Driver IN (reversal), System.MASTER IN (reversal)
   - Mark original PAYOUT as REVERSED

**Business Rules:**
- ✅ Idempotency: Check `idempotencyKey` trước khi tạo
- ✅ Reversal entries: Mark original as REVERSED
- ✅ Ledger correlation: Link refund to original via `groupId` và `note`
- ✅ Double-entry: Refund transactions must balance

**Error Handling:**
- Original not found: Throw `NotFoundException`
- Already refunded: Return existing refund transactions
- Insufficient balance: Should not happen (refund increases balance)

---

## 6. Business Rules & Constraints

### 6.1. Double-Entry Accounting

**Nguyên Tắc:**
- Mọi giao dịch tiền tệ phải có ít nhất 2 entries
- Tổng Debit = Tổng Credit
- Ledger invariant: SUM(all debit) = SUM(all credit)

**Ví Dụ:**

**TOPUP:**
```
System.MASTER OUT (Debit)  = 100,000
User IN (Credit)           = 100,000
────────────────────────────────────
Total                      = 0 ✅
```

**CAPTURE_FARE (Triple-Entry):**
```
Rider OUT (Debit)          = 50,000
Driver IN (Credit)         = 45,000
System.COMMISSION IN (Credit) = 5,000
────────────────────────────────────
Total                      = 0 ✅
```

### 6.2. Idempotency Rules

**Yêu Cầu:**
- Mọi transaction phải có `idempotencyKey` (unique)
- Check idempotency trước khi tạo transaction
- Webhook handlers phải idempotent

**Implementation:**
```java
// Check before create
Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
if (existing.isPresent()) {
    return existing.get(); // Idempotent
}

// Webhook idempotency
List<Transaction> existingSuccess = transactionRepository.findByPspRefAndStatus(pspRef, SUCCESS);
if (!existingSuccess.isEmpty()) {
    return; // Already processed
}
```

### 6.3. Concurrency Safety

**Pessimistic Locking:**
- ✅ `holdAmount()`: Lock wallet trước khi check balance
- ✅ `initPayout()`: Lock wallet trước khi check balance
- ✅ `settleRideFunds()`: Lock cả rider và driver wallets

**Implementation:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.walletId = :walletId")
Optional<Wallet> findByIdWithLock(@Param("walletId") Integer walletId);
```

### 6.4. Transaction Status Flow

**Valid Transitions:**
```
PENDING → SUCCESS ✅
PENDING → FAILED ✅
PENDING → REVERSED ✅ (refund)
SUCCESS → REVERSED ✅ (refund)
```

**Invalid Transitions:**
```
SUCCESS → PENDING ❌
FAILED → SUCCESS ❌
REVERSED → SUCCESS ❌
```

### 6.5. Database Constraints

**Unique Constraints:**
- ✅ `idempotency_key` UNIQUE
- ✅ `psp_ref` UNIQUE

**Nullable Rules:**
- `wallet_id`: NULL cho SYSTEM transactions
- `group_id`: NULL cho single-entry transactions
- `psp_ref`: NULL cho non-PSP transactions

**Amount Rules:**
- `amount` > 0 (should be enforced by application logic)

### 6.6. Balance Consistency Rules

**Available Balance:**
- ✅ Chỉ tính từ transactions SUCCESS
- ✅ CAPTURE_FARE OUT không trừ available
- ✅ HOLD_CREATE trừ available
- ✅ HOLD_RELEASE cộng available

**Pending Balance:**
- ✅ Chỉ tính từ HOLD_CREATE, HOLD_RELEASE, CAPTURE_FARE OUT
- ✅ Pending >= 0 (không bao giờ âm)
- ✅ CAPTURE_FARE OUT trừ pending

**Total Balance:**
- ✅ Total = Available + Pending
- ✅ Balance từ ledger = BalanceCalculationService = UI display

---

## 7. Flow Diagrams

### 7.1. Top-Up Flow

```
┌─────────┐
│  User   │
└────┬────┘
     │ 1. Request Top-up
     ▼
┌─────────────────┐
│ initTopup()     │
│ - Create PENDING│
│   transactions  │
│ - System OUT    │
│ - User IN       │
└────┬────────────┘
     │ 2. Payment Link
     ▼
┌─────────┐
│   PSP   │
└────┬────┘
     │ 3. User Pays
     ▼
┌─────────┐
│ Webhook │
└────┬────┘
     │
     ├─► SUCCESS ──► handleTopupSuccess()
     │                - Update → SUCCESS
     │                - Balance ↑
     │
     └─► FAILED ────► handleTopupFailed()
                      - Update → FAILED
                      - Balance unchanged
```

### 7.2. Hold & Release Flow

```
┌─────────┐
│  Rider  │
└────┬────┘
     │ 1. Book Ride
     ▼
┌─────────────────┐
│ holdAmount()    │
│ - Lock wallet   │
│ - Check balance │
│ - Create HOLD   │
└────┬────────────┘
     │
     ├─► Ride Completed ──► settleRideFunds()
     │                        - CAPTURE_FARE
     │                        - Pending ↓
     │
     └─► Ride Cancelled ──► releaseHold()
                            - Create RELEASE
                            - Available ↑
                            - Pending ↓
```

### 7.3. Capture Fare Flow

```
┌─────────┐
│  Ride   │
│Complete │
└────┬────┘
     │
     ▼
┌─────────────────────┐
│ settleRideFunds()   │
│ - Lock wallets      │
│ - Check idempotency │
│ - Validate balance  │
└────┬────────────────┘
     │
     ▼
┌─────────────────────┐
│ Create 3 Transactions│
│ - Rider OUT         │
│ - Driver IN         │
│ - Commission IN     │
└────┬────────────────┘
     │
     ▼
┌─────────────────────┐
│ Balance Impact:      │
│ - Rider: Pending ↓  │
│ - Driver: Avail ↑   │
│ - System: Comm ↑    │
└─────────────────────┘
```

### 7.4. Payout Flow

```
┌─────────┐
│ Driver  │
└────┬────┘
     │ 1. Request Payout
     ▼
┌─────────────────┐
│ initPayout()    │
│ - Lock wallet   │
│ - Check balance │
│ - Create PENDING│
└────┬────────────┘
     │ 2. PSP Processing
     ▼
┌─────────┐
│ Webhook │
└────┬────┘
     │
     ├─► SUCCESS ──► handlePayoutSuccess()
     │                - Update → SUCCESS
     │                - Balance ↓
     │
     └─► FAILED ────► handlePayoutFailed()
                      - Update → REVERSED
                      - Create REFUND
                      - Balance restored
```

---

## 8. Edge Cases & Error Handling

### 8.1. Race Conditions

**Scenario:** 2 concurrent requests cùng lúc

**Protection:**
- ✅ Pessimistic locking (SELECT FOR UPDATE)
- ✅ Idempotency checks
- ✅ Database constraints (UNIQUE)

**Example:**
```java
// Concurrent holdAmount() calls
Thread 1: Lock wallet → Check balance → Create HOLD
Thread 2: Wait for lock → Check balance → Create HOLD (if still sufficient)
```

### 8.2. Webhook Retries

**Scenario:** PSP gửi webhook nhiều lần

**Protection:**
- ✅ Idempotency check: `findByPspRefAndStatus(pspRef, SUCCESS)`
- ✅ Nếu đã SUCCESS → Skip processing

**Example:**
```java
List<Transaction> existingSuccess = transactionRepository
    .findByPspRefAndStatus(pspRef, TransactionStatus.SUCCESS);
if (!existingSuccess.isEmpty()) {
    return; // Already processed, idempotent
}
```

### 8.3. Orphaned Holds

**Scenario:** HOLD_CREATE không có HOLD_RELEASE sau 7 ngày

**Protection:**
- ✅ `HoldOrphanCleanupScheduler` (daily at 3 AM)
- ✅ Auto-release orphaned holds

**Example:**
```java
@Scheduled(cron = "0 0 3 * * *")
public void cleanupOrphanedHolds() {
    // Find HOLD_CREATE > 7 days without RELEASE
    // Auto-release
}
```

### 8.4. Insufficient Balance

**Scenario:** User không đủ tiền cho hold/payout

**Protection:**
- ✅ Check balance trước khi tạo transaction
- ✅ Lock wallet để prevent race condition
- ✅ Throw `ValidationException`

**Example:**
```java
BigDecimal available = balanceCalculationService.calculateAvailableBalance(walletId);
if (available.compareTo(amount) < 0) {
    throw new ValidationException("Insufficient balance");
}
```

### 8.5. Duplicate Transactions

**Scenario:** User gửi request 2 lần (network retry)

**Protection:**
- ✅ Idempotency key check
- ✅ Return existing transaction

**Example:**
```java
String idempotencyKey = "TOPUP_" + pspRef + "_" + amount;
Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
if (existing.isPresent()) {
    return existing.get(); // Idempotent
}
```

### 8.6. Ledger Invariant Violation

**Scenario:** Tổng debit ≠ tổng credit

**Protection:**
- ✅ `LedgerValidationService` (hourly validation)
- ✅ Throw `ValidationException` nếu violation

**Example:**
```java
@Scheduled(cron = "0 0 * * * *") // Hourly
public void validateLedgerInvariants() {
    BigDecimal totalDebit = calculateTotalDebit();
    BigDecimal totalCredit = calculateTotalCredit();
    if (totalDebit.compareTo(totalCredit) != 0) {
        throw new ValidationException("Ledger invariant violation");
    }
}
```

---

## 9. Best Practices

### 9.1. Transaction Creation

**DO:**
- ✅ Always create transactions with `groupId` for related entries
- ✅ Set `idempotencyKey` for idempotency
- ✅ Set `beforeAvail`/`afterAvail` snapshots for audit
- ✅ Lock wallet trước khi check balance
- ✅ Invalidate cache sau khi tạo transaction

**DON'T:**
- ❌ Không update balance trực tiếp trong Wallet entity
- ❌ Không tạo transaction mà không có idempotency check
- ❌ Không check balance mà không lock wallet

### 9.2. Balance Calculation

**DO:**
- ✅ Always use `BalanceCalculationService` (SSOT)
- ✅ Cache balance trong Redis
- ✅ Invalidate cache khi có transaction mới

**DON'T:**
- ❌ Không đọc balance từ Wallet entity
- ❌ Không cache balance quá lâu (TTL 5 phút)

### 9.3. Error Handling

**DO:**
- ✅ Check idempotency trước khi tạo transaction
- ✅ Validate balance trước khi tạo transaction
- ✅ Lock wallet để prevent race condition
- ✅ Log errors với đầy đủ context

**DON'T:**
- ❌ Không throw exception mà không log
- ❌ Không skip idempotency check

### 9.4. Testing

**DO:**
- ✅ Test idempotency (duplicate requests)
- ✅ Test concurrency (race conditions)
- ✅ Test balance consistency
- ✅ Test ledger invariants
- ✅ Test edge cases (orphaned holds, webhook retries)

**DON'T:**
- ❌ Không test với real database (dùng test database)
- ❌ Không test balance calculation với hardcoded values

---

## 10. Tổng Kết

### 10.1. Key Principles

1. **SSOT (Single Source of Truth):**
   - Balance được tính từ Transaction ledger
   - Wallet entity chỉ chứa metadata

2. **Double-Entry Accounting:**
   - Mọi transaction phải balanced
   - Ledger invariant: SUM(debit) = SUM(credit)

3. **Idempotency:**
   - Mọi transaction phải có idempotency key
   - Webhook handlers phải idempotent

4. **Concurrency Safety:**
   - Pessimistic locking cho critical operations
   - Lock wallet trước khi check balance

5. **Audit Trail:**
   - Mọi thay đổi đều có transaction record
   - Balance snapshots (beforeAvail/afterAvail)

### 10.2. Transaction Types Summary

| Type | Direction | Balance Impact | Double-Entry |
|------|-----------|---------------|--------------|
| TOPUP | IN | Available ↑ | System OUT + User IN |
| HOLD_CREATE | INTERNAL | Available ↓, Pending ↑ | Single entry |
| HOLD_RELEASE | INTERNAL | Available ↑, Pending ↓ | Single entry |
| CAPTURE_FARE | OUT/IN | Pending ↓, Available ↑ | Triple-entry |
| PAYOUT | OUT | Available ↓ | User OUT + System OUT |
| REFUND | IN/OUT | Available ↑/↓ | User + System |
| PROMO_CREDIT | IN | Available ↑ | System OUT + User IN |
| ADJUSTMENT | IN/OUT | Available ↑/↓ | Single entry |

### 10.3. Balance Calculation Summary

**Available Balance:**
```
= SUM(
    + TOPUP IN
    + REFUND IN
    + CAPTURE_FARE IN (driver)
    - PAYOUT OUT
    - HOLD_CREATE
    + HOLD_RELEASE
)
WHERE status = 'SUCCESS'
```

**Pending Balance:**
```
= SUM(
    + HOLD_CREATE
    - HOLD_RELEASE
    - CAPTURE_FARE OUT
)
WHERE status = 'SUCCESS'
```

**Total Balance:**
```
= Available + Pending
```

---

## 11. References

- [FINTECH_COMPREHENSIVE_VERIFICATION.md](./FINTECH_COMPREHENSIVE_VERIFICATION.md) - Comprehensive verification checklist
- [FINTECH_DOUBLE_ENTRY_VERIFICATION.md](./FINTECH_DOUBLE_ENTRY_VERIFICATION.md) - Double-entry accounting verification
- [FINTECH_WALLET_BALANCE_CONSISTENCY.md](./FINTECH_WALLET_BALANCE_CONSISTENCY.md) - Wallet balance consistency
- [FINTECH_TOPUP_FLOW_VERIFICATION.md](./FINTECH_TOPUP_FLOW_VERIFICATION.md) - Top-up flow verification

---

**Document Version:** 1.0  
**Last Updated:** 2024  
**Author:** System Analysis

