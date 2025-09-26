package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response")
public class ErrorResponse {

    @JsonProperty("trace_id")
    @Schema(description = "Trace ID for debugging", example = "550e8400-e29b-41d4-a716-446655440000")
    private String traceId;

    @Schema(description = "Structured error information")
    private ErrorDetail error;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(description = "Timestamp of the error", example = "2025-09-25T09:00:00Z")
    private LocalDateTime timestamp;

    @Schema(description = "Request path that caused the error", example = "/api/v1/...")
    private String path;

    @JsonProperty("field_errors")
    @Schema(description = "Field-specific validation errors")
    private Map<String, String> fieldErrors;

    // Backward compatibility fields - will be deprecated in future versions
    @Deprecated
    @Schema(description = "Legacy error code field - use error.id instead", example = "CONFLICT")
    private String legacyError;

    @Deprecated
    @Schema(description = "Legacy error message field - use error.message instead", example = "Email already exists")
    private String message;
}
