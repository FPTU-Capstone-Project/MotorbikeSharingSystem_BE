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
@Schema(description = "Error response")
public class ErrorResponse {

    @Schema(description = "Error code", example = "CONFLICT")
    private String error;

    @Schema(description = "Error message", example = "Email already exists")
    private String message;

    @JsonProperty("trace_id")
    @Schema(description = "Trace ID for debugging", example = "550e8400-e29b-41d4-a716-446655440000")
    private String traceId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(description = "Timestamp of the error", example = "2025-09-13T09:00:00Z")
    private LocalDateTime timestamp;

    @Schema(description = "Request path that caused the error", example = "/api/v1/auth/register")
    private String path;

    @JsonProperty("field_errors")
    @Schema(description = "Field-specific validation errors")
    private Map<String, String> fieldErrors;
}
