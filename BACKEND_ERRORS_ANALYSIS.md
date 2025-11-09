# Phân Tích Lỗi Backend - Motorbike Sharing System

**Ngày tạo:** 2025-01-27  
**Phạm vi:** Toàn bộ backend project (MotorbikeSharingSystem_BE)

---

## Tổng Quan

Tài liệu này ghi lại các lỗi và vấn đề tiềm ẩn được phát hiện trong quá trình phân tích codebase backend. Các lỗi được phân loại theo mức độ nghiêm trọng và ảnh hưởng.

---

## 1. LỖI NGHIÊM TRỌNG (CRITICAL)

### 1.1. Optional.get() Không Kiểm Tra - Nguy Cơ NoSuchElementException

**Vị trí:**
- `SharedRideServiceImpl.java:86`
- `RefreshTokenServiceImpl.java:69, 99, 131`
- `RideMatchingCoordinator.java:134, 181, 223, 260, 486, 506, 518, 703`

**Mô tả:**
Sử dụng `Optional.get()` trực tiếp mà không kiểm tra `isPresent()` trước, có thể gây ra `NoSuchElementException` khi Optional rỗng.

**Ví dụ:**
```java
// SharedRideServiceImpl.java:86
SharedRide lastRide = latestRide.get(); // Không kiểm tra isPresent()
```

**Ảnh hưởng:**
- **API Layer:** Gây ra lỗi 500 Internal Server Error không mong muốn
- **Business Logic:** Có thể làm gián đoạn flow xử lý ride matching, ride creation
- **User Experience:** Người dùng nhận được lỗi không rõ ràng
- **Logging:** Khó debug vì exception không được log đúng cách

**Giải pháp:**
```java
// Thay vì:
SharedRide lastRide = latestRide.get();

// Nên dùng:
SharedRide lastRide = latestRide
    .orElseThrow(() -> BaseDomainException.of("ride.not-found.latest-ride"));
```

---

### 1.2. Null Pointer Exception Tiềm Ẩn trong WalletServiceImpl

**Vị trí:** `WalletServiceImpl.java:102-104`

**Mô tả:**
Truy cập `wallet.getPendingBalance()` và `wallet.getTotalToppedUp()` mà không kiểm tra null, có thể gây NPE nếu các field này là null.

**Code hiện tại:**
```java
wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
wallet.setShadowBalance(shadowBalance.add(amount));
wallet.setTotalToppedUp(wallet.getTotalToppedUp().add(amount));
```

**Ảnh hưởng:**
- **Transaction Layer:** Có thể làm rollback transaction không mong muốn
- **Wallet Operations:** Ảnh hưởng đến các thao tác chuyển tiền, top-up
- **Data Integrity:** Có thể gây mất dữ liệu nếu transaction bị rollback

**Giải pháp:**
```java
BigDecimal pendingBalance = wallet.getPendingBalance() != null 
    ? wallet.getPendingBalance() 
    : BigDecimal.ZERO;
BigDecimal totalToppedUp = wallet.getTotalToppedUp() != null 
    ? wallet.getTotalToppedUp() 
    : BigDecimal.ZERO;

wallet.setPendingBalance(pendingBalance.subtract(amount));
wallet.setShadowBalance(shadowBalance.add(amount));
wallet.setTotalToppedUp(totalToppedUp.add(amount));
```

---

### 1.3. CompletableFuture.get() Không Có Timeout

**Vị trí:**
- `ProfileServiceImpl.java:331`
- `MessageServiceImpl.java:233`

**Mô tả:**
Sử dụng `CompletableFuture.get()` mà không có timeout, có thể gây thread blocking vô thời hạn nếu async operation bị treo.

**Code hiện tại:**
```java
// ProfileServiceImpl.java:331
String profilePhotoUrl = fileUploadService.uploadFile(avatarFile).get();

// MessageServiceImpl.java:233
String imageUrl = uploadFuture.get(); // Wait for upload to complete
```

**Ảnh hưởng:**
- **Performance:** Thread pool có thể bị cạn kiệt
- **User Experience:** Request bị hang, không có response
- **Resource Leak:** Thread bị block không thể giải phóng
- **Scalability:** Ảnh hưởng đến khả năng xử lý concurrent requests

**Giải pháp:**
```java
// Thêm timeout và xử lý exception
try {
    String profilePhotoUrl = fileUploadService.uploadFile(avatarFile)
        .get(30, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    log.error("File upload timeout for user: {}", username, e);
    throw BaseDomainException.of("file.operation.upload-timeout");
} catch (ExecutionException e) {
    log.error("File upload failed for user: {}", username, e);
    throw BaseDomainException.of("file.operation.upload-failed");
}
```

---

## 2. LỖI QUAN TRỌNG (HIGH PRIORITY)

### 2.1. Code Bị Comment Out - BookingWalletServiceImpl

**Vị trí:** `BookingWalletServiceImpl.java` (toàn bộ file)

**Mô tả:**
Toàn bộ class `BookingWalletServiceImpl` bị comment out, nhưng interface `BookingWalletService` có thể vẫn đang được sử dụng ở đâu đó.

**Ảnh hưởng:**
- **Dependency Injection:** Spring có thể fail khi khởi động nếu có bean dependency
- **Compilation:** Nếu interface vẫn được inject, sẽ gây lỗi runtime
- **Maintenance:** Code không rõ ràng, khó maintain

**Giải pháp:**
1. Kiểm tra xem `BookingWalletService` có đang được sử dụng không
2. Nếu không dùng: Xóa cả interface và implementation
3. Nếu đang dùng: Uncomment và fix các lỗi (nếu có)
4. Nếu đang refactor: Tạo TODO và document rõ ràng

---

### 2.2. @Transactional Bị Comment Out

**Vị trí:** `TransactionServiceImpl.java:275, 322, 449`

**Mô tả:**
Các method có annotation `@Transactional` bị comment out, có thể gây vấn đề về transaction management.

**Code hiện tại:**
```java
//    @Override
//    @Transactional
//    public List<Transaction> createHold(Integer riderId, BigDecimal amount, Integer bookingId, String description) {
```

**Ảnh hưởng:**
- **Data Consistency:** Các thao tác database không được đảm bảo atomicity
- **Rollback:** Không thể rollback khi có lỗi
- **Concurrency:** Có thể gây race condition

**Giải pháp:**
1. Xác định xem các method này có đang được sử dụng không
2. Nếu đang dùng: Uncomment `@Transactional` và test kỹ
3. Nếu không dùng: Xóa method hoặc document rõ lý do

---

### 2.3. RuntimeException Thay Vì Domain Exception

**Vị trí:**
- `RefreshTokenServiceImpl.java:51, 116`
- `ProfileServiceImpl.java:329, 338`

**Mô tả:**
Ném `RuntimeException` thay vì sử dụng domain exception (`BaseDomainException`), không tuân theo error handling pattern của project.

**Code hiện tại:**
```java
// RefreshTokenServiceImpl.java:51
throw new RuntimeException("Failed to generate refresh token", e);

// ProfileServiceImpl.java:329
User user = userRepository.findByEmail(username)
    .orElseThrow(() -> new RuntimeException("User not found"));
```

**Ảnh hưởng:**
- **Error Handling:** GlobalExceptionHandler không thể map đúng error response
- **Error Catalog:** Không sử dụng được error catalog system
- **Consistency:** Không nhất quán với phần còn lại của codebase
- **Client Experience:** Error response không chuẩn, khó xử lý ở client

**Giải pháp:**
```java
// Thay vì:
throw new RuntimeException("Failed to generate refresh token", e);

// Nên dùng:
throw BaseDomainException.of("auth.operation.token-generation-failed", 
    "Failed to generate refresh token", e);

// Thay vì:
.orElseThrow(() -> new RuntimeException("User not found"));

// Nên dùng:
.orElseThrow(() -> BaseDomainException.of("user.not-found.by-username"));
```

---

## 3. LỖI TRUNG BÌNH (MEDIUM PRIORITY)

### 3.1. Thiếu Null Check Trước Khi Truy Cập Nested Object

**Vị trí:** `RideMatchingCoordinator.java:204, 217-223`

**Mô tả:**
Truy cập nested object (`request.getSharedRide().getDriver().getDriverId()`) mà không kiểm tra null ở các level trung gian.

**Code hiện tại:**
```java
JoinRequestSession session = new JoinRequestSession(
    request.getSharedRideRequestId(),
    request.getSharedRide().getSharedRideId(), // Có thể null
    request.getSharedRide().getDriver().getDriverId(), // Có thể NPE
    ...
);
```

**Ảnh hưởng:**
- **Runtime Error:** NPE khi `getSharedRide()` hoặc `getDriver()` trả về null
- **Data Integrity:** Có thể có dữ liệu không nhất quán trong database

**Giải pháp:**
```java
if (request.getSharedRide() == null) {
    throw BaseDomainException.of("ride.not-found.for-request");
}
if (request.getSharedRide().getDriver() == null) {
    throw BaseDomainException.of("ride.validation.driver-missing");
}

JoinRequestSession session = new JoinRequestSession(
    request.getSharedRideRequestId(),
    request.getSharedRide().getSharedRideId(),
    request.getSharedRide().getDriver().getDriverId(),
    ...
);
```

---

### 3.2. Exception Handling Quá Rộng

**Vị trí:**
- `RefreshTokenServiceImpl.java:49, 87, 114, 140`
- `WalletServiceImpl.java:170`
- `SharedRideServiceImpl.java:162`

**Mô tả:**
Catch `Exception` quá rộng, không phân biệt các loại exception khác nhau, khó xử lý đúng cách.

**Code hiện tại:**
```java
try {
    // ...
} catch (Exception e) {
    log.error("Error...", e);
    throw new RuntimeException("Failed to...", e);
}
```

**Ảnh hưởng:**
- **Error Handling:** Khó phân biệt lỗi business logic vs system error
- **Debugging:** Khó trace root cause
- **Recovery:** Không thể xử lý recovery phù hợp cho từng loại lỗi

**Giải pháp:**
```java
try {
    // ...
} catch (BaseDomainException e) {
    // Re-throw domain exceptions
    throw e;
} catch (DataAccessException e) {
    log.error("Database error...", e);
    throw BaseDomainException.of("system.database.error", "Database operation failed", e);
} catch (Exception e) {
    log.error("Unexpected error...", e);
    throw BaseDomainException.of("system.internal.unexpected", "An unexpected error occurred", e);
}
```

---

### 3.3. Thiếu Validation Cho Authentication Parameter

**Vị trí:** `WalletController.java:45, 61, 80, 98`

**Mô tả:**
Các controller method nhận `Authentication` parameter nhưng không kiểm tra null trước khi sử dụng.

**Code hiện tại:**
```java
@GetMapping("/balance")
public ResponseEntity<WalletResponse> getBalance(Authentication authentication) {
    log.info("Get balance request from user: {}", authentication.getName()); // Có thể NPE
    ...
}
```

**Ảnh hưởng:**
- **Security:** Có thể bypass authentication nếu có lỗi config
- **Runtime Error:** NPE nếu authentication không được inject đúng

**Giải pháp:**
```java
@GetMapping("/balance")
public ResponseEntity<WalletResponse> getBalance(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
        throw BaseDomainException.of("auth.unauthorized.missing-authentication");
    }
    log.info("Get balance request from user: {}", authentication.getName());
    ...
}
```

**Lưu ý:** Spring Security thường đảm bảo authentication không null, nhưng nên có defensive check.

---

## 4. LỖI THẤP (LOW PRIORITY) - CODE QUALITY

### 4.1. Code Comment Out Không Rõ Ràng

**Vị trí:**
- `SharedRideServiceImpl.java:176-179, 199-200, 284-285`
- `RideMatchingCoordinator.java:194-199`

**Mô tả:**
Có nhiều đoạn code bị comment out mà không có comment giải thích lý do.

**Ảnh hưởng:**
- **Maintainability:** Khó hiểu tại sao code bị comment
- **Code Review:** Khó quyết định có nên xóa hay giữ lại

**Giải pháp:**
1. Thêm comment giải thích lý do comment out
2. Hoặc xóa code nếu không cần thiết
3. Hoặc tạo TODO nếu đang refactor

---

### 4.2. Magic Numbers và Hard-coded Values

**Vị trí:** Nhiều nơi trong codebase

**Mô tả:**
Có các giá trị hard-coded như timeout, limit, etc. không được định nghĩa trong config.

**Ví dụ:**
```java
// RefreshTokenServiceImpl.java:42
refreshToken.setExpiresAt(LocalDateTime.now().plusDays(30)); // Magic number
```

**Giải pháp:**
Đưa vào configuration properties:
```java
@Value("${app.refresh-token.expiry-days:30}")
private int refreshTokenExpiryDays;
```

---

## 5. TỔNG KẾT VÀ KHUYẾN NGHỊ

### Ưu Tiên Sửa Lỗi

1. **Ngay lập tức (P0):**
   - Sửa tất cả `Optional.get()` không kiểm tra
   - Fix NPE trong `WalletServiceImpl.transferPendingToAvailable`
   - Thêm timeout cho `CompletableFuture.get()`

2. **Sớm (P1):**
   - Uncomment hoặc xóa `BookingWalletServiceImpl`
   - Fix `@Transactional` bị comment
   - Thay `RuntimeException` bằng `BaseDomainException`

3. **Trung hạn (P2):**
   - Cải thiện exception handling
   - Thêm null checks cho nested objects
   - Clean up commented code

4. **Dài hạn (P3):**
   - Refactor magic numbers
   - Cải thiện code documentation

### Testing Recommendations

Sau khi fix các lỗi, nên test:

1. **Unit Tests:**
   - Test các method có `Optional.get()` với empty Optional
   - Test null scenarios cho wallet operations
   - Test timeout scenarios cho file upload

2. **Integration Tests:**
   - Test transaction rollback scenarios
   - Test error handling flow
   - Test concurrent requests

3. **E2E Tests:**
   - Test ride creation flow
   - Test wallet operations
   - Test file upload operations

---

## 6. CÁC PHẦN BỊ ẢNH HƯỞNG

### 6.1. Ride Management
- **SharedRideService:** Có thể fail khi tạo ride nếu Optional rỗng
- **RideMatchingCoordinator:** Matching flow có thể bị gián đoạn

### 6.2. Wallet & Transaction
- **WalletService:** NPE khi transfer pending to available
- **TransactionService:** Transaction không được đảm bảo atomicity

### 6.3. Authentication & Authorization
- **RefreshTokenService:** Error handling không chuẩn
- **Auth Flow:** Có thể gây confusion cho client

### 6.4. File Upload
- **ProfileService:** Upload có thể bị hang
- **MessageService:** Image upload có thể block thread

---

## 7. METRICS VÀ MONITORING

Sau khi fix, nên monitor:

1. **Error Rate:** Giảm số lượng 500 errors
2. **Response Time:** Cải thiện timeout handling
3. **Transaction Success Rate:** Đảm bảo transaction consistency
4. **Thread Pool Usage:** Monitor thread blocking

---

**Kết luận:** Backend project có một số lỗi cần được xử lý, đặc biệt là các lỗi liên quan đến null handling và exception management. Việc fix các lỗi này sẽ cải thiện đáng kể độ ổn định và khả năng maintain của hệ thống.

