package com.mssus.app.common.exception;

import com.mssus.app.common.exception.catalog.ErrorCatalogService;
import com.mssus.app.common.exception.catalog.ErrorEntry;
import com.mssus.app.dto.response.ErrorDetail;
import com.mssus.app.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler that provides unified error handling across the application.
 * Uses the error catalog system for consistent error responses.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    
    private final ErrorCatalogService errorCatalogService;

    /**
     * Handle BaseDomainException - the primary exception handler for catalog-backed errors
     */
    @ExceptionHandler(BaseDomainException.class)
    public ResponseEntity<ErrorResponse> handleBaseDomainException(BaseDomainException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        
        // Log with appropriate level based on error severity
        logException(traceId, ex);
        
        ErrorResponse errorResponse = buildErrorResponse(ex.getErrorEntry(), ex.getErrorId(), 
                ex.getMessage(), ex.getContext(), traceId, request);
        
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }
    
    /**
     * Handle legacy NotFoundException - maps to catalog entry
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.info("NotFoundException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("user.not-found.by-id", ex.getMessage());
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "user.not-found.by-id", 
                ex.getMessage(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Handle legacy ConflictException - maps to catalog entry
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("ConflictException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("user.conflict.email-exists", ex.getMessage());
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "user.conflict.email-exists", 
                ex.getMessage(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
    
    /**
     * Handle legacy UnauthorizedException - maps to catalog entry
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("UnauthorizedException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("auth.unauthorized.access-denied", ex.getMessage());
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "auth.unauthorized.access-denied", 
                ex.getMessage(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    /**
     * Handle ForbiddenException - maps to catalog entry
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("ForbiddenException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("auth.unauthorized.access-denied", ex.getMessage());
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "auth.unauthorized.access-denied", 
                ex.getMessage(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle legacy ValidationException - maps to catalog entry
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("ValidationException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("validation.request.invalid-body", ex.getMessage());
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "validation.request.invalid-body", 
                ex.getMessage(), null, traceId, request);
        
        // Add field errors if present
        if (ex.getFieldErrors() != null) {
            errorResponse.setFieldErrors(ex.getFieldErrors());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle Spring validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("MethodArgumentNotValidException [{}]: Validation failed", traceId);
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("validation.request.invalid-body");
        
        // Extract field errors
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "validation.request.invalid-body", 
                "Validation failed for request body", null, traceId, request);
        errorResponse.setFieldErrors(fieldErrors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("MissingServletRequestParameterException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("validation.request.missing-parameter");
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "validation.request.missing-parameter", 
                message, null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle type mismatch errors
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("MethodArgumentTypeMismatchException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("validation.request.invalid-parameter-type");
        String message = String.format("Parameter '%s' has invalid type", ex.getName());
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "validation.request.invalid-parameter-type", 
                message, null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle IllegalArgumentException - often from enum conversion errors
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("IllegalArgumentException [{}]: {}", traceId, ex.getMessage());

        String errorId;
        String message;
        Map<String, String> fieldErrors = null;

        String exceptionMessage = ex.getMessage();

        // Handle enum constant errors
        if (exceptionMessage != null && exceptionMessage.startsWith("No enum constant")) {
            // Extract enum class and invalid value from: "No enum constant com.mssus.app.common.enums.OtpFor.VERIFY_MAIL"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("No enum constant ([^.]+\\.)+([^.]+)\\.(.+)");
            java.util.regex.Matcher matcher = pattern.matcher(exceptionMessage);

            if (matcher.find()) {
                String enumClass = matcher.group(2); // e.g., "OtpFor"
                String invalidValue = matcher.group(3); // e.g., "VERIFY_MAIL"

                errorId = "validation.request.invalid-enum-value";
                message = String.format("Invalid value '%s' for field type %s", invalidValue, enumClass);

                // Create field error map
                fieldErrors = new HashMap<>();
                fieldErrors.put(enumClass.toLowerCase(), message);
            } else {
                errorId = "validation.request.invalid-enum-value";
                message = "Invalid enum value provided";
            }
        } else {
            // Handle other IllegalArgumentException cases
            errorId = "validation.request.invalid-argument";
            message = exceptionMessage != null ? exceptionMessage : "Invalid argument provided";
        }

        ErrorEntry errorEntry = errorCatalogService.getErrorEntry(errorId);

        ErrorResponse errorResponse = buildErrorResponse(errorEntry, errorId,
            message, null, traceId, request);

        // Add field errors if present
        if (fieldErrors != null) {
            errorResponse.setFieldErrors(fieldErrors);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle malformed JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("HttpMessageNotReadableException [{}]: {}", traceId, ex.getMessage());

        String errorId = "validation.request.invalid-body";
        String message = parseJsonError(ex);
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("validation.request.invalid-body");
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, errorId,
                message, null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    private String parseJsonError(HttpMessageNotReadableException ex) {
        String originalMessage = ex.getMessage();
        Throwable cause = ex.getCause();

        if (cause != null) {
            String causeMessage = cause.getMessage();

            // Handle unknown/unrecognized fields
            if (causeMessage.contains("Unrecognized field")) {
                // Extract field name from: "Unrecognized field \"fieldName\" (class com.example.Class), not marked as ignorable"
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Unrecognized field \"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(causeMessage);
                if (matcher.find()) {
                    String fieldName = matcher.group(1);
                    return String.format("Field '%s' does not exist and is not allowed", fieldName);
                }
                return "Request contains unrecognized fields";
            }

            // Handle invalid enum values
            if (causeMessage.contains("not one of the values accepted for Enum")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("value '([^']+)'");
                java.util.regex.Matcher matcher = pattern.matcher(causeMessage);
                if (matcher.find()) {
                    String invalidValue = matcher.group(1);
                    return String.format("Invalid enum value: \"%s\"", invalidValue);
                }
                return "Invalid enum value provided";
            }

            // Handle type mismatch (e.g., string instead of number)
            if (causeMessage.contains("Cannot deserialize value")) {
                if (causeMessage.contains("from String")) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("from String \"([^\"]+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(causeMessage);
                    if (matcher.find()) {
                        String invalidValue = matcher.group(1);
                        return String.format("Invalid value format: \"%s\"", invalidValue);
                    }
                }
                return "Invalid data type for field";
            }

            // Handle missing required fields (JSON parsing level)
            if (causeMessage.contains("required") || causeMessage.contains("missing")) {
                return "Required field is missing from request";
            }

            // Handle malformed JSON syntax
            if (causeMessage.contains("Unexpected character") ||
                causeMessage.contains("Unexpected end-of-input") ||
                causeMessage.contains("was expecting")) {
                return "Request body contains invalid JSON syntax";
            }
        }

        // Default fallback message
        return "Request body is malformed or unreadable";
    }
    
    /**
     * Handle file size exceeded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("MaxUploadSizeExceededException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("validation.file.size-exceeded");
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "validation.file.size-exceeded", 
                errorEntry.getMessageTemplate(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Handle Spring Security authentication errors
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("AuthenticationException [{}]: {}", traceId, ex.getMessage());
        
        String errorId = ex instanceof BadCredentialsException ? 
                "auth.validation.invalid-credentials" : "auth.unauthorized.token-invalid";
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry(errorId);
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, errorId, 
                errorEntry.getMessageTemplate(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }
    
    /**
     * Handle Spring Security access denied errors
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.warn("AccessDeniedException [{}]: {}", traceId, ex.getMessage());
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("auth.unauthorized.access-denied");
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "auth.unauthorized.access-denied", 
                errorEntry.getMessageTemplate(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        String traceId = generateTraceId();
        log.error("Unexpected exception [{}]: ", traceId, ex);
        
        ErrorEntry errorEntry = errorCatalogService.getErrorEntry("system.internal.unexpected");
        
        ErrorResponse errorResponse = buildErrorResponse(errorEntry, "system.internal.unexpected", 
                errorEntry.getMessageTemplate(), null, traceId, request);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Build standardized error response from error entry
     */
    private ErrorResponse buildErrorResponse(ErrorEntry errorEntry, String errorId, String message, 
                                           Map<String, Object> context, String traceId, HttpServletRequest request) {
        
        ErrorDetail errorDetail = ErrorDetail.builder()
                .id(errorId)
                .message(message)
                .domain(errorEntry.getDomain())
                .category(errorEntry.getCategory())
                .severity(errorEntry.getSeverity())
                .retryable(errorEntry.getIsRetryable())
                .remediation(errorEntry.getRemediation())
                .build();
        
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .traceId(traceId)
                .error(errorDetail)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI());
        
        // Set legacy fields for backward compatibility
        builder.legacyError(errorId)
               .message(message);
        
        return builder.build();
    }
    
    /**
     * Log exception with appropriate level based on severity
     */
    private void logException(String traceId, BaseDomainException ex) {
        String severity = ex.getSeverity();
        String message = "{} [{}]: {} (errorId: {})";
        Object[] args = {ex.getClass().getSimpleName(), traceId, ex.getMessage(), ex.getErrorId()};
        
        switch (severity.toUpperCase()) {
            case "INFO" -> log.info(message, args);
            case "WARN" -> log.warn(message, args);
            case "ERROR" -> log.error(message, args);
            default -> log.warn(message, args);
        }
        
        // Log context if present
        if (!ex.getContext().isEmpty()) {
            log.debug("Exception context [{}]: {}", traceId, ex.getContext());
        }
    }
    
    /**
     * Generate unique trace ID for error correlation
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString();
    }
}