//package com.mssus.app.controller;
//
//import com.mssus.app.common.exception.catalog.ErrorCatalogService;
//import com.mssus.app.common.exception.catalog.ErrorEntry;
//import com.mssus.app.dto.response.ErrorDetail;
//import com.mssus.app.dto.response.ErrorResponse;
//import jakarta.servlet.RequestDispatcher;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.web.servlet.error.ErrorController;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
//@RestController
//@RequiredArgsConstructor
//@Slf4j
//public class CustomErrorController implements ErrorController {
//
//    private final ErrorCatalogService errorCatalogService;
//
//    @RequestMapping("/error")
//    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
//        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
//        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
//        String traceId = UUID.randomUUID().toString();
//
//        // Determine error ID based on status code
//        String errorId = determineErrorId(statusCode);
//        ErrorEntry errorEntry = errorCatalogService.getErrorEntry(errorId);
//
//        // Build structured error response
//        ErrorDetail errorDetail = ErrorDetail.builder()
//            .id(errorId)
//            .message(errorEntry.getMessageTemplate())
//            .domain(errorEntry.getDomain())
//            .category(errorEntry.getCategory())
//            .severity(errorEntry.getSeverity())
//            .retryable(errorEntry.getIsRetryable())
//            .remediation(errorEntry.getRemediation())
//            .build();
//
//        ErrorResponse errorResponse = ErrorResponse.builder()
//            .traceId(traceId)
//            .error(errorDetail)
//            .timestamp(LocalDateTime.now())
//            .path(requestUri)
//            // Legacy fields for backward compatibility
//            .legacyError(errorId)
//            .message(errorEntry.getMessageTemplate())
//            .build();
//
//        HttpStatus httpStatus = statusCode != null ?
//            HttpStatus.valueOf(statusCode) : HttpStatus.INTERNAL_SERVER_ERROR;
//
//        log.warn("Error handled by CustomErrorController [{}]: {} - {}",
//            traceId, statusCode, requestUri);
//
//        return ResponseEntity.status(httpStatus).body(errorResponse);
//    }
//
//    private String determineErrorId(Integer statusCode) {
//        if (statusCode == null) {
//            return "system.internal.unexpected";
//        }
//
//        return switch (statusCode) {
//            case 400, 405 -> "validation.request.invalid-body";
//            case 401 -> "auth.unauthorized.token-invalid";
//            case 403 -> "auth.unauthorized.access-denied";
//            case 404 -> "user.not-found.by-id";  // Generic not found
//            case 503 -> "system.internal.service-unavailable";
//            default -> "system.internal.unexpected";
//        };
//    }
//}