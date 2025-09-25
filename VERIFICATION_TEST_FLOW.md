# 🧪 Verification System Test Flow

This document provides a comprehensive testing flow for the verification system covering both student and driver verification processes.

## 📋 Prerequisites

1. **Admin User**: Create an admin account for approval operations
2. **Student Users**: Create test student accounts
3. **Driver Candidates**: Create test users who want to become drivers
4. **Test Documents**: Prepare sample documents (images/PDFs)

## 🏗️ Test Data Setup

### 1. Create Test Users

```sql
-- Create test student
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type)
VALUES ('student1@test.edu', '0901234567', '$2a$10$hash', 'Nguyen Van A', 'SV001', 'student');

-- Create test driver candidate
INSERT INTO users (email, phone, password_hash, full_name, student_id, user_type)
VALUES ('driver1@test.com', '0901234568', '$2a$10$hash', 'Tran Van B', 'SV002', 'student');

-- Create admin user
INSERT INTO users (email, phone, password_hash, full_name, user_type)
VALUES ('admin@test.com', '0901234569', '$2a$10$hash', 'Admin User', 'admin');

-- Create admin profile
INSERT INTO admin_profiles (admin_id, department, permissions)
VALUES (3, 'Verification', '["verify_students", "verify_drivers"]');
```

### 2. Authentication Setup

First, authenticate as each user to get JWT tokens:

```bash
# Student login
POST /api/auth/login
{
  "email": "student1@test.edu",
  "password": "password123"
}

# Driver candidate login
POST /api/auth/login
{
  "email": "driver1@test.com",
  "password": "password123"
}

# Admin login
POST /api/auth/login
{
  "email": "admin@test.com",
  "password": "admin123"
}
```

## 🎓 Student Verification Test Flow

### Step 1: Student Submits Verification
**Endpoint**: `POST /api/v1/users/me/student-verifications`
**Authorization**: Bearer {student_token}
**Content-Type**: multipart/form-data

```bash
curl -X POST http://localhost:8080/api/v1/users/me/student-verifications \
  -H "Authorization: Bearer {student_token}" \
  -F "document=@student_id_card.jpg"
```

**Expected Response**:
```json
{
  "verification_id": 1,
  "user_id": 1,
  "type": "student_id",
  "status": "pending",
  "document_url": "/uploads/verifications/1_student_id.jpg",
  "document_type": "image",
  "created_at": "2025-09-25T10:30:00"
}
```

### Step 2: Admin Views Pending Student Verifications
**Endpoint**: `GET /api/v1/verification/students/pending`
**Authorization**: Bearer {admin_token}

```bash
curl -X GET "http://localhost:8080/api/v1/verification/students/pending?page=0&size=10" \
  -H "Authorization: Bearer {admin_token}"
```

**Expected Response**:
```json
{
  "data": [
    {
      "verification_id": 1,
      "user_id": 1,
      "full_name": "Nguyen Van A",
      "email": "student1@test.edu",
      "phone": "0901234567",
      "student_id": "SV001",
      "status": "pending",
      "document_url": "/uploads/verifications/1_student_id.jpg",
      "document_type": "image",
      "created_at": "2025-09-25T10:30:00"
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 10,
    "totalPages": 1,
    "totalRecords": 1
  }
}
```

### Step 3: Admin Reviews Student Details
**Endpoint**: `GET /api/v1/verification/students/{id}`
**Authorization**: Bearer {admin_token}

```bash
curl -X GET http://localhost:8080/api/v1/verification/students/1 \
  -H "Authorization: Bearer {admin_token}"
```

### Step 4: Admin Approves Student (Success Case)
**Endpoint**: `POST /api/v1/verification/students/{id}/approve`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/students/1/approve \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "notes": "Student ID verified successfully"
  }'
```

**Expected Response**:
```json
{
  "message": "Student verification approved successfully"
}
```

### Step 5: Admin Rejects Student (Failure Case)
**Endpoint**: `POST /api/v1/verification/students/{id}/reject`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/students/2/reject \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "rejectionReason": "Document is not clear, please resubmit",
    "notes": "Photo quality is poor"
  }'
```

### Step 6: Bulk Approval Test
**Endpoint**: `POST /api/v1/verification/students/bulk-approve`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/students/bulk-approve \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "verificationIds": [3, 4, 5],
    "notes": "Batch approved after document review"
  }'
```

## 🚗 Driver Verification Test Flow

### Step 1: User Submits Driver Verification
**Endpoint**: `POST /api/v1/users/me/driver-verifications`
**Authorization**: Bearer {driver_candidate_token}
**Content-Type**: multipart/form-data

```bash
curl -X POST http://localhost:8080/api/v1/users/me/driver-verifications \
  -H "Authorization: Bearer {driver_candidate_token}" \
  -F "driverLicense=@driver_license.jpg" \
  -F "vehicleRegistration=@vehicle_reg.jpg" \
  -F "vehicleModel=Honda Wave Alpha" \
  -F "plateNumber=29A-12345" \
  -F "year=2020" \
  -F "color=Red" \
  -F "licenseNumber=B12345678"
```

**Expected Response**:
```json
{
  "verification_id": 10,
  "user_id": 2,
  "type": "driver_license",
  "status": "pending",
  "document_url": "/uploads/verifications/2_driver_license.jpg",
  "document_type": "image",
  "created_at": "2025-09-25T11:00:00"
}
```

### Step 2: Admin Views Pending Driver Verifications
**Endpoint**: `GET /api/v1/verification/drivers/pending`
**Authorization**: Bearer {admin_token}

```bash
curl -X GET "http://localhost:8080/api/v1/verification/drivers/pending?page=0&size=10" \
  -H "Authorization: Bearer {admin_token}"
```

**Expected Response**:
```json
{
  "data": [
    {
      "user_id": 2,
      "full_name": "Tran Van B",
      "email": "driver1@test.com",
      "phone": "0901234568",
      "license_number": "B12345678",
      "driver_status": "pending",
      "verifications": [
        {
          "verification_id": 10,
          "type": "driver_license",
          "status": "pending",
          "document_url": "/uploads/verifications/2_driver_license.jpg",
          "created_at": "2025-09-25T11:00:00"
        },
        {
          "verification_id": 11,
          "type": "vehicle_registration",
          "status": "pending",
          "document_url": "/uploads/verifications/2_vehicle_reg.jpg",
          "created_at": "2025-09-25T11:00:00"
        }
      ],
      "created_at": "2025-09-25T11:00:00"
    }
  ],
  "pagination": {
    "page": 1,
    "pageSize": 10,
    "totalPages": 1,
    "totalRecords": 1
  }
}
```

### Step 3: Admin Reviews Driver KYC Details
**Endpoint**: `GET /api/v1/verification/drivers/{id}/kyc`
**Authorization**: Bearer {admin_token}

```bash
curl -X GET http://localhost:8080/api/v1/verification/drivers/2/kyc \
  -H "Authorization: Bearer {admin_token}"
```

### Step 4: Admin Approves Driver Documents
**Endpoint**: `POST /api/v1/verification/drivers/{id}/approve-docs`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/drivers/2/approve-docs \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "notes": "All documents verified successfully"
  }'
```

### Step 5: Admin Approves Driver License
**Endpoint**: `POST /api/v1/verification/drivers/{id}/approve-license`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/drivers/2/approve-license \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "notes": "License verified against government database"
  }'
```

### Step 6: Admin Approves Vehicle Registration
**Endpoint**: `POST /api/v1/verification/drivers/{id}/approve-vehicle`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/drivers/2/approve-vehicle \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "notes": "Vehicle registration confirmed"
  }'
```

### Step 7: Admin Conducts Background Check
**Endpoint**: `PUT /api/v1/verification/drivers/{id}/background-check`
**Authorization**: Bearer {admin_token}

```bash
# Success case
curl -X PUT http://localhost:8080/api/v1/verification/drivers/2/background-check \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "result": "passed",
    "details": "No criminal record found",
    "conductedBy": "Admin Team"
  }'

# Failure case
curl -X PUT http://localhost:8080/api/v1/verification/drivers/3/background-check \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "result": "failed",
    "details": "Criminal record found",
    "conductedBy": "Admin Team"
  }'
```

### Step 8: Admin Rejects Driver (Complete Rejection)
**Endpoint**: `POST /api/v1/verification/drivers/{id}/reject`
**Authorization**: Bearer {admin_token}

```bash
curl -X POST http://localhost:8080/api/v1/verification/drivers/4/reject \
  -H "Authorization: Bearer {admin_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "rejectionReason": "Fake documents detected",
    "notes": "Documents appear to be forged"
  }'
```

## 📊 Admin Dashboard & Statistics

### View Driver Verification Statistics
**Endpoint**: `GET /api/v1/verification/drivers/stats`
**Authorization**: Bearer {admin_token}

```bash
curl -X GET http://localhost:8080/api/v1/verification/drivers/stats \
  -H "Authorization: Bearer {admin_token}"
```

**Expected Response**:
```json
{
  "total_drivers": 150,
  "pending_verifications": 25,
  "pending_documents": 10,
  "pending_licenses": 8,
  "pending_vehicles": 7,
  "pending_background_checks": 5,
  "approved_drivers": 100,
  "rejected_verifications": 15,
  "completion_rate": 85.5
}
```

### View Verification History
**Endpoint**: `GET /api/v1/verification/students/history`
**Authorization**: Bearer {admin_token}

```bash
curl -X GET "http://localhost:8080/api/v1/verification/students/history?page=0&size=20" \
  -H "Authorization: Bearer {admin_token}"
```

## 🧪 Test Scenarios

### Scenario 1: Happy Path - Student Approval
1. Student submits verification → Status: `pending`
2. Admin reviews and approves → Status: `approved`
3. Student can now access student-only features

### Scenario 2: Student Rejection & Resubmission
1. Student submits unclear document → Status: `pending`
2. Admin rejects with reason → Status: `rejected`
3. Student resubmits better document → New verification created
4. Admin approves → Status: `approved`

### Scenario 3: Driver Complete KYC Flow
1. User submits driver verification → Driver profile created, Status: `pending`
2. Admin approves documents → Document verification: `approved`
3. Admin approves license → License verification: `approved`, license_verified_at updated
4. Admin approves vehicle → Vehicle verification: `approved`
5. Admin passes background check → Background check: `passed`, Driver status: `active`

### Scenario 4: Driver Rejection at Different Stages
1. **Early rejection**: Admin rejects after document review
2. **License rejection**: Documents OK, but fake license detected
3. **Background check failure**: All documents OK, but criminal record found

### Scenario 5: Bulk Operations
1. Multiple students submit verifications
2. Admin bulk approves valid ones
3. System processes each verification atomically
4. Returns success/failure report

## 📝 Validation Tests

### Input Validation Tests
- Submit verification with missing documents
- Submit with invalid file types
- Submit rejection without reason
- Submit background check with invalid result

### Security Tests
- Access admin endpoints without admin token
- Access other user's verification details
- Submit verification without authentication

### Edge Cases
- Submit duplicate verification
- Approve already approved verification
- Background check on non-existent driver
- Bulk approve with mix of valid/invalid IDs

## 🎯 Expected Outcomes

After running the complete test flow:

1. **Student Verifications**:
   - Approved students can access student features
   - Rejected students receive clear rejection reasons
   - Admin has full audit trail

2. **Driver Verifications**:
   - Fully approved drivers have status `active`
   - Partial approvals keep status `pending`
   - Failed background checks result in `rejected` status
   - Vehicle information is properly linked

3. **Statistics Dashboard**:
   - Accurate counts for all verification states
   - Completion rates calculated correctly
   - Real-time updates as verifications are processed

## 🔧 Troubleshooting

### Common Issues
1. **File upload failures**: Check file size limits and supported formats
2. **Permission errors**: Verify admin authentication and permissions
3. **Status not updating**: Check transaction boundaries in service methods
4. **Foreign key errors**: Ensure proper entity relationships

### Debug Endpoints
- Check user profiles: `GET /api/v1/users/me`
- Check verification status: `GET /api/v1/verifications/{id}`
- Check driver profile: `GET /api/v1/drivers/{id}`

This comprehensive test flow ensures all verification scenarios are covered and the system works as expected.