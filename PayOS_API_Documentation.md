# PayOS Integration API Documentation

## Overview
PayOS integration cho phép người dùng nạp tiền vào ví thông qua payment gateway PayOS. Flow bao gồm tạo payment link và xử lý webhook để cập nhật số dư ví.

## Payment Flow

### 1. Create Top-up Payment Link
Tạo link thanh toán để người dùng nạp tiền vào ví.

**Endpoint:** `POST /api/v1/payos/create-topup-link`

**Parameters:**
- `userId` (required): ID của user
- `amount` (required): Số tiền nạp (VND)
- `description` (optional): Mô tả giao dịch (default: "Wallet Top-up")

**Example Request:**
```http
POST /api/v1/payos/create-topup-link?userId=1&amount=500000&description=Nap tien vi
```

**Response:**
```json
{
  "bin": "970422",
  "accountNumber": "12345678",
  "accountName": "NGUYEN VAN A",
  "amount": 500000,
  "description": "Nap tien vi",
  "orderCode": 1698765432,
  "currency": "VND",
  "paymentLinkId": "abcd1234",
  "status": "PENDING",
  "checkoutUrl": "https://pay.payos.vn/web/abcd1234",
  "qrCode": "https://api.payos.vn/qr/abcd1234.png"
}
```

**Flow sau khi tạo payment link:**
1. Transaction được tạo với status `PENDING`
2. `pendingBalance` trong ví được tăng
3. User được redirect đến `checkoutUrl` để thanh toán

### 2. Test Payment Link (for development)
**Endpoint:** `POST /api/v1/payos/test`

**Example Request:**
```http
POST /api/v1/payos/test
```

### 3. Webhook Handler
PayOS sẽ gọi webhook khi có thay đổi trạng thái thanh toán.

**Endpoint:** `POST /api/v1/payos/webhook`

**Webhook Payload Example:**
```json
{
  "data": {
    "orderCode": "1698765432",
    "amount": 500000,
    "description": "Nap tien vi",
    "accountNumber": "12345678",
    "reference": "REF123",
    "transactionDateTime": "2024-01-01T10:00:00Z",
    "status": "PAID",
    "currency": "VND"
  },
  "desc": "success",
  "success": true,
  "signature": "signature_string"
}
```

**Webhook Processing:**
- **Status `PAID` hoặc `PROCESSING`:**
  - Transaction status → `COMPLETED`
  - Chuyển tiền từ `pendingBalance` sang `shadowBalance`
  - Cập nhật `totalToppedUp`

- **Status `CANCELLED` hoặc `EXPIRED`:**
  - Transaction status → `FAILED`
  - Giảm `pendingBalance`

## Database Schema

### Transactions Table
```sql
- txn_id (BIGSERIAL PRIMARY KEY)
- type (VARCHAR) - "TOP_UP"
- direction (VARCHAR) - "INBOUND"
- actor_kind (VARCHAR) - "USER"
- actor_user_id (INTEGER)
- amount (DECIMAL)
- currency (VARCHAR) - "VND"
- psp_ref (VARCHAR) - orderCode từ PayOS
- status (VARCHAR) - "PENDING", "COMPLETED", "FAILED"
- note (TEXT)
- created_at (TIMESTAMP)
```

### Wallets Table
```sql
- wallet_id (INTEGER PRIMARY KEY)
- user_id (INTEGER FOREIGN KEY)
- shadow_balance (DECIMAL) - Số dư khả dụng
- pending_balance (DECIMAL) - Số dư đang chờ xử lý
- total_topped_up (DECIMAL) - Tổng số tiền đã nạp
- total_spent (DECIMAL) - Tổng số tiền đã chi
- is_active (BOOLEAN)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)
```

## Error Handling

### Common Error Responses

**400 Bad Request:**
```json
{
  "error": "Amount must be greater than zero",
  "timestamp": "2024-01-01T10:00:00Z",
  "status": 400
}
```

**404 Not Found:**
```json
{
  "error": "Wallet not found for user: 1",
  "timestamp": "2024-01-01T10:00:00Z",
  "status": 404
}
```

**500 Internal Server Error:**
```json
{
  "error": "Error creating payment link",
  "timestamp": "2024-01-01T10:00:00Z",
  "status": 500
}
```

## Testing Scenarios

### Scenario 1: Successful Top-up
1. Call `POST /api/v1/payos/create-topup-link`
2. User completes payment on PayOS
3. PayOS sends webhook with status `PAID`
4. Verify transaction status is `COMPLETED`
5. Verify wallet balance updated

### Scenario 2: Failed/Cancelled Payment
1. Call `POST /api/v1/payos/create-topup-link`
2. User cancels payment
3. PayOS sends webhook with status `CANCELLED`
4. Verify transaction status is `FAILED`
5. Verify pending balance decreased

### Scenario 3: Expired Payment
1. Call `POST /api/v1/payos/create-topup-link`
2. Payment link expires
3. PayOS sends webhook with status `EXPIRED`
4. Verify transaction status is `FAILED`

## Configuration

### Required Environment Variables
```properties
payos.client-id=your_client_id
payos.api-key=your_api_key
payos.checksum-key=your_checksum_key
payos.return-url=http://localhost:3000/payment/success
payos.cancel-url=http://localhost:3000/payment/cancel
```

### Webhook URL Configuration
Configure in PayOS dashboard:
```
https://your-domain.com/api/v1/payos/webhook
```

## Security Notes

1. **Webhook Verification:** Verify webhook signature using checksum key
2. **Amount Validation:** Always validate payment amounts
3. **Idempotency:** Handle duplicate webhook calls
4. **Transaction State:** Use database transactions for consistency
5. **Logging:** Log all payment activities for audit trail

## Monitoring & Logging

### Important Logs to Monitor
- Payment link creation
- Webhook processing
- Transaction status changes
- Wallet balance updates
- Error scenarios

### Metrics to Track
- Successful vs failed payments
- Payment completion time
- Webhook processing latency
- User top-up patterns