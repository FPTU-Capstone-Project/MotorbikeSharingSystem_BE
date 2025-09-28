package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error detail object that contains structured error information
 * Part of the standardized error response format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error detail information")
public class ErrorDetail {
    
    @Schema(description = "Unique error identifier", example = "auth.validation.missing-credential")
    private String id;
    
    @Schema(description = "Human-readable error message", example = "Authentication credentials are required")
    private String message;
    
    @Schema(description = "Additional error details", example = "The Authorization header is missing")
    private String details;
    
    @Schema(description = "Error domain", example = "auth")
    private String domain;
    
    @Schema(description = "Error category", example = "validation")
    private String category;
    
    @Schema(description = "Error severity level", example = "WARN")
    private String severity;
    
    @Schema(description = "Whether this error is retryable", example = "false")
    private Boolean retryable;
    
    @Schema(description = "Suggested remediation", example = "Provide valid credentials in the request")
    private String remediation;
}
