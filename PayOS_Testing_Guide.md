# PayOS Testing Guide

## 🚀 Quick Start

### 1. Import Postman Collection
1. Mở Postman
2. Click **Import** → **Upload Files**
3. Chọn file `PayOS_Postman_Collection.json`
4. Collection sẽ được import với các test cases đã chuẩn bị

### 2. Setup Environment Variables
Trong Postman, tạo environment với các variables:
```
base_url: http://localhost:8080
user_id: 1
```

### 3. Prerequisites
Đảm bảo:
- ✅ Application đang chạy trên port 8080
- ✅ Database đã có user với ID = 1
- ✅ User đã có wallet được tạo
- ✅ PayOS credentials đã được config trong `application.properties`

## 📋 Test Scenarios

### Scenario 1: Successful Payment Flow
```bash
1. POST /create-topup-link (userId=1, amount=500000)
   → Tạo payment link thành công
   → Transaction được tạo với status PENDING
   → Pending balance tăng

2. POST /webhook (status=PAID)
   → Transaction status → COMPLETED
   → Pending balance → Shadow balance
   → Total topped up tăng
```

### Scenario 2: Cancelled Payment Flow
```bash
1. POST /create-topup-link (userId=1, amount=100000)
   → Tạo payment link thành công

2. POST /webhook (status=CANCELLED)
   → Transaction status → FAILED
   → Pending balance giảm về 0
```

### Scenario 3: Error Handling
```bash
1. POST /create-topup-link (amount=0)
   → Should return 400/500 error

2. POST /create-topup-link (userId=99999)
   → Should return 404/500 error

3. POST /webhook (malformed JSON)
   → Should return 500 error
```

## 🔧 Manual Testing Steps

### Step 1: Verify Database State
```sql
-- Check initial wallet state
SELECT * FROM wallets WHERE user_id = 1;

-- Check transactions
SELECT * FROM transactions WHERE actor_user_id = 1 ORDER BY created_at DESC;
```

### Step 2: Create Payment Link
```http
POST {{base_url}}/api/v1/payos/create-topup-link
?userId=1&amount=500000&description=Test top-up

Expected Response:
- Status: 200
- checkoutUrl: PayOS payment URL
- orderCode: Unique order ID
- amount: 500000
```

### Step 3: Verify Database After Creation
```sql
-- Should see new PENDING transaction
SELECT * FROM transactions WHERE psp_ref = '{orderCode}';

-- Should see increased pending balance
SELECT pending_balance FROM wallets WHERE user_id = 1;
```

### Step 4: Simulate Successful Payment
```http
POST {{base_url}}/api/v1/payos/webhook
Content-Type: application/json

{
  "data": {
    "orderCode": "{orderCode_from_step2}",
    "status": "PAID",
    "amount": 500000,
    ...
  }
}
```

### Step 5: Verify Final State
```sql
-- Transaction should be COMPLETED
SELECT status FROM transactions WHERE psp_ref = '{orderCode}';

-- Pending balance should be 0, shadow balance increased
SELECT shadow_balance, pending_balance, total_topped_up
FROM wallets WHERE user_id = 1;
```

## 🧪 Automated Tests

### Run Collection in Postman
1. Select collection "PayOS Integration API"
2. Click **Run** button
3. Configure:
   - Environment: Your test environment
   - Iterations: 1
   - Delay: 1000ms between requests
4. Click **Run PayOS Integration API**

### Expected Results
- ✅ All basic API tests should pass
- ✅ Error handling tests should return appropriate status codes
- ✅ Database state should be consistent

## 🔍 Debugging Guide

### Common Issues

**1. PayOS Configuration Error**
```
Error: Failed to initialize PayOS client
```
**Solution:** Check `application.properties`:
```properties
payos.client-id=your_client_id
payos.api-key=your_api_key
payos.checksum-key=your_checksum_key
payos.return-url=http://localhost:3000/success
payos.cancel-url=http://localhost:3000/cancel
```

**2. Wallet Not Found Error**
```
Error: Wallet not found for user: 1
```
**Solution:** Create wallet for user:
```sql
INSERT INTO wallets (user_id, shadow_balance, pending_balance, total_topped_up, total_spent, is_active)
VALUES (1, 0.00, 0.00, 0.00, 0.00, true);
```

**3. Transaction Not Found Error**
```
Error: Pending transaction not found for pspRef: 123456
```
**Solution:**
- Verify orderCode in webhook matches created transaction
- Check transaction status is PENDING before webhook

### Debugging SQL Queries

**Check Transaction Flow:**
```sql
-- All transactions for user
SELECT txn_id, type, status, amount, psp_ref, created_at
FROM transactions
WHERE actor_user_id = 1
ORDER BY created_at DESC;

-- Wallet balance history
SELECT shadow_balance, pending_balance, total_topped_up, updated_at
FROM wallets
WHERE user_id = 1;

-- Failed transactions
SELECT * FROM transactions WHERE status = 'FAILED';
```

## 📊 Performance Testing

### Load Testing with Postman
1. Set up collection with realistic data
2. Configure multiple iterations (10-100)
3. Add random delays
4. Monitor response times

### Monitoring Points
- API response time < 2s
- Database transaction commits
- Memory usage during high load
- PayOS API rate limits

## 🔒 Security Testing

### Webhook Security
- [ ] Verify webhook signature validation
- [ ] Test with invalid signatures
- [ ] Test with replay attacks
- [ ] Test with malformed payloads

### Input Validation
- [ ] SQL injection in parameters
- [ ] XSS in description fields
- [ ] Large amount values
- [ ] Special characters in user inputs

## 📝 Test Report Template

```markdown
## PayOS Integration Test Report

**Date:** [DATE]
**Tester:** [NAME]
**Environment:** [DEV/STAGING/PROD]

### Test Summary
- Total Tests: [NUMBER]
- Passed: [NUMBER]
- Failed: [NUMBER]
- Success Rate: [PERCENTAGE]

### Failed Tests
| Test Case | Expected | Actual | Issue |
|-----------|----------|--------|--------|
| [NAME] | [EXPECTED] | [ACTUAL] | [DESCRIPTION] |

### Performance Metrics
- Average Response Time: [TIME]ms
- Maximum Response Time: [TIME]ms
- Database Query Time: [TIME]ms

### Issues Found
1. [ISSUE DESCRIPTION]
2. [ISSUE DESCRIPTION]

### Recommendations
1. [RECOMMENDATION]
2. [RECOMMENDATION]
```

## 🔄 Continuous Testing

### Integration with CI/CD
```yaml
# GitHub Actions example
- name: Run PayOS API Tests
  run: |
    newman run PayOS_Postman_Collection.json \
      --environment test.postman_environment.json \
      --reporters cli,junit \
      --reporter-junit-export results.xml
```

### Monitoring Webhooks
Set up monitoring cho webhook endpoint:
- Response time alerts
- Error rate thresholds
- Failed payment notifications
- Database consistency checks