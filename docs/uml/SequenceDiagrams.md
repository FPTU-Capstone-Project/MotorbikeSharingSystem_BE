# Sequence Diagrams

This document shows the step-by-step processes and interactions in the MSSUS Account module.

## Overview

**For Non-Technical Readers**: These diagrams show the sequence of steps that happen when users perform actions like registering, logging in, or updating their profile. Think of them as detailed workflows showing who does what and when.

**For Technical Readers**: These UML sequence diagrams illustrate the interaction flows between system components, showing message passing, timing, and control flow for key use cases.

---

## 1. User Registration Flow

### Complete Registration Process

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant UserRepo as UserRepository
    participant RiderRepo as RiderProfileRepository  
    participant WalletRepo as WalletRepository
    participant JWT as JwtService
    participant DB as Database

    Client->>Controller: POST /auth/register
    Note over Client,Controller: {email, password, fullName, ...}

    Controller->>Controller: Validate @Valid annotations
    alt Validation Failed
        Controller->>Client: 400 Bad Request
    end

    Controller->>Service: register(RegisterRequest)
    
    Service->>UserRepo: existsByEmail(email)
    UserRepo->>DB: SELECT COUNT(*) FROM users WHERE email = ?
    DB-->>UserRepo: result
    UserRepo-->>Service: boolean exists
    
    alt Email Already Exists
        Service-->>Controller: throw ConflictException
        Controller-->>Client: 409 Conflict
    end

    Service->>UserRepo: existsByPhone(phone)
    UserRepo->>DB: SELECT COUNT(*) FROM users WHERE phone = ?
    DB-->>UserRepo: result
    UserRepo-->>Service: boolean exists
    
    alt Phone Already Exists
        Service-->>Controller: throw ConflictException
        Controller-->>Client: 409 Conflict
    end

    Service->>Service: passwordEncoder.encode(password)
    Service->>UserRepo: save(UserEntity)
    UserRepo->>DB: INSERT INTO users (...)
    DB-->>UserRepo: user_id = 1
    UserRepo-->>Service: UserEntity with ID

    Service->>RiderRepo: save(RiderProfileEntity)
    RiderRepo->>DB: INSERT INTO rider_profiles (rider_id, ...)
    DB-->>RiderRepo: success
    RiderRepo-->>Service: RiderProfileEntity

    Service->>WalletRepo: save(WalletEntity)  
    WalletRepo->>DB: INSERT INTO wallets (user_id, ...)
    DB-->>WalletRepo: wallet_id = 1
    WalletRepo-->>Service: WalletEntity

    Service->>JWT: generateToken(userEmail, claims)
    JWT-->>Service: JWT token string

    Service->>Service: buildRegisterResponse()
    Service-->>Controller: RegisterResponse
    Controller-->>Client: 201 Created + RegisterResponse

    Note over Client,DB: Registration complete with JWT token
```

### Registration Error Handling

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant ExceptionHandler as GlobalExceptionHandler

    Client->>Controller: POST /auth/register (invalid data)

    alt Input Validation Error
        Controller->>Controller: @Valid validation fails
        Controller->>ExceptionHandler: MethodArgumentNotValidException
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler-->>Client: 400 Bad Request + ErrorResponse
    end

    alt Business Logic Error
        Controller->>Service: register(request)
        Service->>Service: Check business rules
        Service-->>Controller: throw ConflictException("Email exists")
        Controller->>ExceptionHandler: ConflictException
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler-->>Client: 409 Conflict + ErrorResponse
    end

    alt Unexpected Error
        Controller->>Service: register(request)
        Service->>Service: Unexpected database error
        Service-->>Controller: throw RuntimeException
        Controller->>ExceptionHandler: Exception
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler->>ExceptionHandler: Log error details
        ExceptionHandler-->>Client: 500 Internal Server Error
    end
```

---

## 2. User Login Flow

### Successful Login Process

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant UserDetails as CustomUserDetailsService
    participant UserRepo as UserRepository
    participant JWT as JwtService
    participant DB as Database

    Client->>Controller: POST /auth/login
    Note over Client,Controller: {email, password}

    Controller->>Service: login(LoginRequest)
    
    Service->>UserDetails: loadUserByUsername(email)
    UserDetails->>UserRepo: findByEmailWithProfiles(email)
    UserRepo->>DB: SELECT u.*, r.*, d.* FROM users u LEFT JOIN ...
    DB-->>UserRepo: User + Profiles data
    UserRepo-->>UserDetails: UserEntity with profiles
    
    UserDetails->>UserDetails: Create UserPrincipal with authorities
    UserDetails-->>Service: UserDetails object

    Service->>Service: passwordEncoder.matches(rawPassword, encodedPassword)
    
    alt Password Mismatch
        Service-->>Controller: throw UnauthorizedException
        Controller-->>Client: 401 Unauthorized
    end

    alt Account Inactive
        Service->>Service: Check user.isActive()
        Service-->>Controller: throw UnauthorizedException("Account disabled")
        Controller-->>Client: 401 Unauthorized
    end

    Service->>Service: determineActiveProfiles(user)
    Service->>JWT: generateToken(email, profileClaims)
    JWT-->>Service: JWT access token

    Service->>JWT: generateRefreshToken(email)
    JWT-->>Service: JWT refresh token

    Service->>Service: buildLoginResponse()
    Service-->>Controller: LoginResponse
    Controller-->>Client: 200 OK + LoginResponse

    Note over Client,DB: Login successful with tokens
```

### Multi-Profile Login Claims

```mermaid
sequenceDiagram
    participant Service as AuthService
    participant JWT as JwtService
    participant User as UserEntity

    Service->>User: getRiderProfile()
    User-->>Service: RiderProfileEntity or null

    Service->>User: getDriverProfile()  
    User-->>Service: DriverProfileEntity or null

    Service->>User: getAdminProfile()
    User-->>Service: AdminProfileEntity or null

    Service->>Service: buildTokenClaims()
    Note over Service: {<br/>  "userId": 1,<br/>  "email": "user@uni.edu.vn",<br/>  "profiles": ["rider", "driver"],<br/>  "currentProfile": "rider",<br/>  "authorities": ["ROLE_RIDER", "ROLE_DRIVER"]<br/>}

    Service->>JWT: generateToken(email, claims)
    JWT->>JWT: Jwts.builder().setClaims(claims)
    JWT-->>Service: Signed JWT token
```

---

## 3. Profile Management Flow

### Get User Profile

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Filter as JwtAuthenticationFilter
    participant Controller as AuthController
    participant Service as AuthService
    participant UserRepo as UserRepository
    participant Mapper as UserMapper
    participant DB as Database

    Client->>Filter: GET /profile + Authorization: Bearer <token>
    
    Filter->>Filter: extractToken()
    Filter->>Filter: validateToken()
    Filter->>Filter: setSecurityContext()
    Filter->>Controller: Continue request

    Controller->>Controller: SecurityContextHolder.getContext()
    Controller->>Service: getUserProfile(userId)

    Service->>UserRepo: findByIdWithProfiles(userId)
    UserRepo->>DB: SELECT u.*, r.*, d.*, a.*, w.* FROM users u ...
    DB-->>UserRepo: Complete user data
    UserRepo-->>Service: UserEntity with all profiles

    Service->>Mapper: toProfileResponse(userEntity)
    Mapper-->>Service: UserProfileResponse

    Service-->>Controller: UserProfileResponse
    Controller-->>Client: 200 OK + Profile data
```

### Switch Profile (Rider to Driver)

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant UserRepo as UserRepository
    participant JWT as JwtService
    participant DB as Database

    Client->>Controller: POST /profile/switch
    Note over Client,Controller: {"profileType": "driver"}

    Controller->>Service: switchProfile(userId, "driver")
    
    Service->>UserRepo: findByIdWithProfiles(userId)
    UserRepo->>DB: SELECT u.*, d.* FROM users u LEFT JOIN driver_profiles d ...
    DB-->>UserRepo: User with driver profile
    UserRepo-->>Service: UserEntity

    alt Driver Profile Not Found
        Service-->>Controller: throw NotFoundException("Driver profile not found")
        Controller-->>Client: 404 Not Found
    end

    alt Driver Not Verified
        Service->>Service: Check driverProfile.getStatus()
        Service-->>Controller: throw UnauthorizedException("Driver not verified")
        Controller-->>Client: 401 Unauthorized
    end

    Service->>Service: buildNewTokenClaims(currentProfile = "driver")
    Service->>JWT: generateToken(email, newClaims)
    JWT-->>Service: New JWT token

    Service-->>Controller: SwitchProfileResponse
    Controller-->>Client: 200 OK + New token
```

---

## 4. Password Management Flow

### Change Password

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant UserRepo as UserRepository
    participant Encoder as PasswordEncoder
    participant DB as Database

    Client->>Controller: PUT /auth/change-password
    Note over Client,Controller: {currentPassword, newPassword, confirmPassword}

    Controller->>Service: changePassword(userId, changePasswordRequest)

    Service->>UserRepo: findById(userId)
    UserRepo->>DB: SELECT * FROM users WHERE user_id = ?
    DB-->>UserRepo: UserEntity
    UserRepo-->>Service: UserEntity

    Service->>Encoder: matches(currentPassword, storedHash)
    Encoder-->>Service: boolean matches

    alt Current Password Invalid
        Service-->>Controller: throw UnauthorizedException("Current password invalid")
        Controller-->>Client: 401 Unauthorized
    end

    Service->>Service: validatePasswordStrength(newPassword)
    alt Weak Password
        Service-->>Controller: throw ValidationException("Password too weak")
        Controller-->>Client: 422 Unprocessable Entity
    end

    Service->>Encoder: encode(newPassword)
    Encoder-->>Service: newPasswordHash

    Service->>Service: user.setPasswordHash(newPasswordHash)
    Service->>UserRepo: save(user)
    UserRepo->>DB: UPDATE users SET password_hash = ? WHERE user_id = ?
    DB-->>UserRepo: Update successful
    UserRepo-->>Service: Updated UserEntity

    Service-->>Controller: Success message
    Controller-->>Client: 200 OK + Success message
```

### Forgot Password Flow

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant UserRepo as UserRepository
    participant OtpUtil as OtpUtil
    participant EmailService as EmailService (Future)
    participant Cache as Redis Cache (Future)

    Client->>Controller: POST /auth/forgot-password
    Note over Client,Controller: {"email": "user@uni.edu.vn"}

    Controller->>Service: forgotPassword(email)

    Service->>UserRepo: findByEmail(email)
    UserRepo-->>Service: UserEntity or empty

    alt User Not Found
        Service-->>Controller: Success (security - don't reveal if email exists)
        Controller-->>Client: 200 OK + "OTP sent if email exists"
    end

    Service->>OtpUtil: generateOtp()
    OtpUtil-->>Service: "123456"

    Service->>Cache: store(email, otp, expiration=5min)
    Cache-->>Service: Stored

    Service->>EmailService: sendPasswordResetEmail(email, otp)
    EmailService-->>Service: Email sent

    Service-->>Controller: Success message
    Controller-->>Client: 200 OK + "OTP sent to your email"

    Note over Client,EmailService: User receives email with OTP
```

---

## 5. Driver Verification Flow

### Submit Driver Documents

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant UserRepo as UserRepository
    participant VerificationRepo as VerificationRepository
    participant FileService as FileService (Future)
    participant DB as Database

    Client->>Controller: POST /profile/driver/verification
    Note over Client,Controller: {licenseNumber, documentUrls...}

    Controller->>Service: submitDriverVerification(userId, request)

    Service->>UserRepo: findByIdWithProfiles(userId)
    UserRepo-->>Service: UserEntity with DriverProfile

    alt No Driver Profile
        Service-->>Controller: throw NotFoundException("Driver profile not found")
        Controller-->>Client: 404 Not Found
    end

    Service->>Service: Create VerificationEntity(type="driver_license")
    Service->>VerificationRepo: save(verification)
    VerificationRepo->>DB: INSERT INTO verifications (...)
    DB-->>VerificationRepo: verification_id
    VerificationRepo-->>Service: VerificationEntity

    Service->>Service: Create VerificationEntity(type="identity_card")  
    Service->>VerificationRepo: save(verification)
    VerificationRepo->>DB: INSERT INTO verifications (...)
    DB-->>VerificationRepo: verification_id
    VerificationRepo-->>Service: VerificationEntity

    Service->>Service: updateDriverStatus("pending")
    Service->>UserRepo: save(user)
    UserRepo->>DB: UPDATE driver_profiles SET status = 'pending'
    DB-->>UserRepo: Updated

    Service-->>Controller: VerificationResponse
    Controller-->>Client: 201 Created + Verification details

    Note over Client,DB: Documents submitted, awaiting admin review
```

### Admin Review Process

```mermaid
sequenceDiagram
    participant Admin as Admin User
    participant Controller as AdminController
    participant Service as AdminService
    participant VerificationRepo as VerificationRepository
    participant UserRepo as UserRepository
    participant NotificationService as NotificationService (Future)
    participant DB as Database

    Admin->>Controller: PUT /admin/verifications/{id}
    Note over Admin,Controller: {"status": "approved", "comments": "Documents valid"}

    Controller->>Service: reviewVerification(verificationId, adminId, decision)

    Service->>VerificationRepo: findById(verificationId)
    VerificationRepo-->>Service: VerificationEntity

    Service->>Service: verification.setStatus("approved")
    Service->>Service: verification.setVerifiedBy(adminId)
    Service->>Service: verification.setVerifiedAt(now())

    Service->>VerificationRepo: save(verification)
    VerificationRepo->>DB: UPDATE verifications SET status='approved', verified_by=?, verified_at=?
    DB-->>VerificationRepo: Updated

    Service->>Service: checkAllVerificationsApproved(userId)
    
    alt All Documents Approved
        Service->>UserRepo: updateDriverStatus(userId, "active")
        UserRepo->>DB: UPDATE driver_profiles SET status='active'
        DB-->>UserRepo: Updated

        Service->>NotificationService: sendDriverApprovalNotification(userId)
        NotificationService-->>Service: Notification sent
    end

    Service-->>Controller: Success message
    Controller-->>Admin: 200 OK + Updated verification

    Note over Admin,DB: Driver verification complete
```

---

## 6. OTP Verification Flow

### Request and Submit OTP

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as AuthController
    participant Service as AuthService
    participant OtpUtil as OtpUtil
    participant UserRepo as UserRepository
    participant Cache as Redis Cache (Future)
    participant EmailService as EmailService (Future)
    participant DB as Database

    Client->>Controller: POST /auth/otp/request
    Note over Client,Controller: {"type": "email", "purpose": "verification"}

    Controller->>Service: requestOtp(userId, type, purpose)

    Service->>OtpUtil: generateOtp()
    OtpUtil-->>Service: "123456"

    Service->>Cache: store(key="otp:userId:email", value="123456", ttl=300s)
    Cache-->>Service: Stored

    Service->>UserRepo: findById(userId)
    UserRepo-->>Service: UserEntity

    Service->>EmailService: sendVerificationEmail(user.email, otp)
    EmailService-->>Service: Email sent

    Service-->>Controller: OtpResponse
    Controller-->>Client: 200 OK + "OTP sent to email"

    Note over Client,EmailService: === User receives email ===

    Client->>Controller: POST /auth/otp/submit
    Note over Client,Controller: {"type": "email", "otp": "123456", "purpose": "verification"}

    Controller->>Service: submitOtp(userId, otpRequest)

    Service->>Cache: get(key="otp:userId:email")
    Cache-->>Service: "123456"

    alt OTP Not Found or Expired
        Service-->>Controller: throw UnauthorizedException("Invalid or expired OTP")
        Controller-->>Client: 401 Unauthorized
    end

    Service->>Service: Compare submitted OTP with cached OTP

    alt OTP Mismatch
        Service-->>Controller: throw UnauthorizedException("Invalid OTP")
        Controller-->>Client: 401 Unauthorized
    end

    Service->>Cache: delete(key="otp:userId:email")
    Cache-->>Service: Deleted

    Service->>UserRepo: updateEmailVerified(userId, true)
    UserRepo->>DB: UPDATE users SET email_verified = true WHERE user_id = ?
    DB-->>UserRepo: Updated
    UserRepo-->>Service: Updated UserEntity

    Service-->>Controller: Success message
    Controller-->>Client: 200 OK + "Email verified successfully"
```

---

## 7. JWT Token Validation Flow

### Request Authentication

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Filter as JwtAuthenticationFilter
    participant JWT as JwtService
    participant UserDetails as CustomUserDetailsService
    participant UserRepo as UserRepository
    participant Controller as Protected Controller
    participant DB as Database

    Client->>Filter: Any protected endpoint + Authorization: Bearer <token>

    Filter->>Filter: extractTokenFromHeader()
    
    alt No Token Found
        Filter->>Filter: filterChain.doFilter() (continue without auth)
        Filter-->>Client: 401 Unauthorized (from endpoint)
    end

    Filter->>JWT: extractUsername(token)
    JWT->>JWT: Parse JWT claims
    JWT-->>Filter: username (email)

    Filter->>Filter: SecurityContextHolder.getContext().getAuthentication()

    alt Already Authenticated
        Filter->>Filter: filterChain.doFilter() (skip validation)
    end

    Filter->>UserDetails: loadUserByUsername(username)
    UserDetails->>UserRepo: findByEmailWithProfiles(email)
    UserRepo->>DB: SELECT u.*, r.*, d.*, a.* FROM users u ...
    DB-->>UserRepo: User with profiles
    UserRepo-->>UserDetails: UserEntity

    UserDetails->>UserDetails: mapToUserPrincipal(userEntity)
    UserDetails-->>Filter: UserDetails with authorities

    Filter->>JWT: isTokenValid(token, userDetails)
    JWT->>JWT: Check expiration, signature, claims
    JWT-->>Filter: boolean isValid

    alt Invalid Token
        Filter->>Filter: SecurityContextHolder.clearContext()
        Filter-->>Client: Continue without authentication (401 from endpoint)
    end

    Filter->>Filter: Create UsernamePasswordAuthenticationToken
    Filter->>Filter: SecurityContextHolder.getContext().setAuthentication()
    Filter->>Controller: filterChain.doFilter() (continue with auth)

    Controller->>Controller: Process business logic with authenticated user
    Controller-->>Client: Protected resource response
```

---

## 8. Error Handling Flow

### Global Exception Handling

```mermaid
sequenceDiagram
    participant Client as Client App
    participant Controller as Any Controller
    participant Service as Any Service
    participant ExceptionHandler as GlobalExceptionHandler
    participant Logger as SLF4J Logger

    Client->>Controller: Any request

    Controller->>Service: Business method call

    alt Validation Exception
        Service->>Service: Input validation fails
        Service-->>Controller: throw ValidationException("Invalid email format")
        Controller->>ExceptionHandler: ValidationException
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler->>Logger: log.warn("Validation error: {}", exception.getMessage())
        ExceptionHandler-->>Client: 422 Unprocessable Entity + ErrorResponse
    end

    alt Not Found Exception
        Service->>Service: Resource not found
        Service-->>Controller: throw NotFoundException("User not found")
        Controller->>ExceptionHandler: NotFoundException  
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler->>Logger: log.info("Resource not found: {}", exception.getMessage())
        ExceptionHandler-->>Client: 404 Not Found + ErrorResponse
    end

    alt Conflict Exception
        Service->>Service: Business rule violation
        Service-->>Controller: throw ConflictException("Email already exists")
        Controller->>ExceptionHandler: ConflictException
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler->>Logger: log.warn("Conflict: {}", exception.getMessage())
        ExceptionHandler-->>Client: 409 Conflict + ErrorResponse
    end

    alt Unexpected Exception
        Service->>Service: Unexpected error occurs
        Service-->>Controller: throw RuntimeException("Database connection failed")
        Controller->>ExceptionHandler: Exception
        ExceptionHandler->>ExceptionHandler: generateTraceId()
        ExceptionHandler->>Logger: log.error("Unexpected error", exception)
        ExceptionHandler-->>Client: 500 Internal Server Error + ErrorResponse
    end

    Note over Client,Logger: All errors have trace ID for debugging
```

---

## Summary

These sequence diagrams illustrate:

**✅ Complete User Journeys**:
- Registration with automatic profile creation
- Login with multi-profile token generation
- Profile management and role switching
- Document verification workflow

**✅ Security Flows**:
- JWT token validation and refresh
- Password change and reset processes
- OTP generation and verification
- Multi-layer authentication

**✅ Error Handling**:
- Comprehensive exception handling
- Graceful degradation patterns
- Consistent error response format
- Proper logging and traceability

**✅ System Integration**:
- Controller → Service → Repository flow
- Database transaction management
- External service integration patterns
- Caching and performance optimization

These flows ensure the system handles both happy path and error scenarios while maintaining security, performance, and user experience standards.
