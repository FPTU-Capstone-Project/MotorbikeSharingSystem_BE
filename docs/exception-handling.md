# Exception Handling Architecture

## Overview

The MSSUS backend uses a unified, catalog-based exception handling system that provides consistent error responses across all APIs. This system replaces the previous ad-hoc exception handling with a structured, maintainable approach.

## Architecture Components

### 1. Error Catalog (`errors.yaml`)

The error catalog is the single source of truth for all error definitions in the system. It contains structured error metadata including:

- **Error ID**: Unique identifier in format `domain.category.name`
- **HTTP Status**: Appropriate HTTP status code (400, 401, 404, 500, etc.)
- **Message Template**: User-friendly error message
- **Domain**: Error domain (auth, user, wallet, validation, system)
- **Category**: Error category (validation, not-found, conflict, unauthorized, internal, operation)
- **Severity**: Error severity level (INFO, WARN, ERROR)
- **Retryable**: Whether the error condition is retryable
- **Owner**: Team responsible for this error type
- **Remediation**: Optional guidance for users

**Example:**
```yaml
- id: auth.validation.missing-credential
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Authentication credentials are required"
  domain: auth
  category: validation
  owner: auth-team
  remediation: "Provide valid credentials in the request"
```

### 2. Core Classes

#### `ErrorEntry`
Data class representing a single error catalog entry with all metadata.

#### `ErrorCatalogService`
Service responsible for loading and managing the error catalog. Provides methods to retrieve error entries by ID.

#### `BaseDomainException`
The core exception class that integrates with the error catalog. All domain exceptions should use this class.

**Usage Examples:**
```java
// Using catalog error ID
throw BaseDomainException.of("auth.validation.invalid-credentials");

// With custom message
throw BaseDomainException.of("user.not-found.by-id", "User not found: " + username);

// With formatted message
throw BaseDomainException.formatted("user.not-found.by-id", "User with ID %d not found", userId);

// With context
Map<String, Object> context = Map.of("userId", userId, "operation", "login");
throw BaseDomainException.of("auth.unauthorized.access-denied", null, cause, context);
```

### 3. Error Response Format

The system provides standardized error responses:

```json
{
  "trace_id": "550e8400-e29b-41d4-a716-446655440000",
  "error": {
    "id": "auth.validation.invalid-credentials",
    "message": "Invalid username or password",
    "domain": "auth",
    "category": "validation",
    "severity": "WARN",
    "retryable": false,
    "remediation": "Check credentials and try again"
  },
  "timestamp": "2025-09-25T09:00:00Z",
  "path": "/api/v1/auth/login"
}
```

For backward compatibility, legacy fields are also included:
- `error` (deprecated): Legacy error code
- `message` (deprecated): Legacy error message

### 4. Global Exception Handler

The `GlobalExceptionHandler` provides unified exception handling:
- Maps `BaseDomainException` to standardized responses
- Handles legacy exceptions with catalog lookups
- Maps framework exceptions (validation, security, etc.)
- Provides fallback for unexpected exceptions

## Usage Guide

### Creating New Errors

1. **Add to Error Catalog** (`src/main/resources/errors.yaml`):
```yaml
- id: payment.operation.insufficient-funds
  httpStatus: 400
  severity: WARN
  isRetryable: false
  messageTemplate: "Insufficient funds for this transaction"
  domain: payment
  category: operation
  owner: payment-team
  remediation: "Add funds to your account or reduce transaction amount"
```

2. **Throw in Service Layer**:
```java
if (wallet.getBalance() < amount) {
    throw BaseDomainException.of("payment.operation.insufficient-funds");
}
```

### Error ID Naming Convention

Format: `<domain>.<category>.<name>`

**Domains:**
- `auth` - Authentication and authorization
- `user` - User management
- `wallet` - Wallet operations
- `notification` - Email/SMS notifications
- `validation` - Input validation
- `system` - Internal system errors

**Categories:**
- `validation` - Input validation failures
- `not-found` - Resource not found
- `conflict` - Resource conflicts
- `unauthorized` - Authorization failures
- `operation` - Business operation failures
- `internal` - Internal system errors

### Migration from Legacy Exceptions

**Old Approach:**
```java
throw new NotFoundException("USER_NOT_FOUND", "User not found: " + username);
```

**New Approach:**
```java
throw NotFoundException.userNotFound(username);
// or directly:
throw BaseDomainException.of("user.not-found.by-id", "User not found: " + username);
```

### Factory Methods

Legacy exception classes provide both old constructors (deprecated) and new catalog-based factory methods:

```java
// NotFoundException
NotFoundException.userNotFound(userId)
NotFoundException.resourceNotFound("Vehicle", "ID " + vehicleId)

// ConflictException  
ConflictException.emailAlreadyExists(email)
ConflictException.of("Custom conflict message")

// ValidationException
ValidationException.invalidOtp()
ValidationException.fileTooLarge(maxSize)

// UnauthorizedException
UnauthorizedException.invalidCredentials()
UnauthorizedException.accessDenied()
```

### Field Validation Errors

For validation errors with field-level details:

```java
Map<String, String> fieldErrors = Map.of(
    "email", "Invalid email format",
    "password", "Password too short"
);

ErrorResponse response = buildErrorResponse(
    errorEntry, errorId, message, null, traceId, request);
response.setFieldErrors(fieldErrors);
```

### Logging and Observability

Exceptions are logged with appropriate levels based on severity:
- `INFO`: Expected business logic failures (user not found, etc.)
- `WARN`: Validation errors, conflicts, authorization failures
- `ERROR`: Internal system errors, unexpected exceptions

Log format includes:
- Exception class name
- Trace ID for correlation
- Error message
- Error ID
- Context information (when available)

### Testing Exception Responses

```java
@Test
public void testInvalidCredentials() {
    // When
    ResponseEntity<ErrorResponse> response = testRestTemplate.postForEntity(
        "/api/v1/auth/login", invalidRequest, ErrorResponse.class);
    
    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().getError().getId()).isEqualTo("auth.validation.invalid-credentials");
    assertThat(response.getBody().getTraceId()).isNotNull();
}
```

## Best Practices

### 1. Use Catalog Errors
Always prefer catalog-based errors over hardcoded messages:
```java
// Good
throw BaseDomainException.of("user.validation.invalid-email");

// Bad
throw new ValidationException("INVALID_EMAIL", "Invalid email format");
```

### 2. Provide Context
Include relevant context for debugging:
```java
Map<String, Object> context = Map.of(
    "userId", userId,
    "operation", "transfer",
    "amount", amount
);
throw BaseDomainException.of("wallet.operation.insufficient-balance", null, null, context);
```

### 3. Use Appropriate Severity
- `INFO`: Normal business logic failures
- `WARN`: Client errors, validation failures
- `ERROR`: System errors, unexpected conditions

### 4. Choose Correct HTTP Status
- `400`: Bad request, validation errors
- `401`: Authentication required
- `403`: Forbidden, insufficient permissions
- `404`: Resource not found
- `409`: Conflict, resource already exists
- `500`: Internal server error

### 5. Security Considerations
- Never expose sensitive information in error messages
- Use generic messages for security-sensitive operations
- Log detailed information server-side only

## Error Review Checklist

When adding new errors:

- [ ] Error ID follows naming convention
- [ ] Appropriate HTTP status code
- [ ] User-safe error message
- [ ] Correct severity level
- [ ] Domain and category are accurate
- [ ] Owner team is specified
- [ ] Remediation provided when applicable
- [ ] Tests cover error scenarios
- [ ] Documentation updated if needed

## Backward Compatibility

The system maintains backward compatibility with existing clients:
- Legacy error fields are still populated
- Old exception constructors still work (but are deprecated)
- HTTP status codes remain consistent
- Response format includes both old and new structures

## Migration Status

- ✅ Error catalog created
- ✅ Core infrastructure implemented
- ✅ Global exception handler updated
- ✅ Domain exceptions migrated
- ✅ Service layer partially migrated
- ⚠️  Legacy exceptions deprecated but functional
- 📋 Full service layer migration ongoing

## Future Enhancements

1. **Internationalization**: Support for multiple languages using error IDs
2. **Error Analytics**: Centralized error tracking and analysis
3. **Client SDKs**: Generated client code for error handling
4. **Dynamic Catalog**: Runtime error catalog management
5. **Error Webhooks**: External notifications for critical errors
