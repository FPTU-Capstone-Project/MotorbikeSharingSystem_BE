package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Background check request")
public class BackgroundCheckRequest {

    @NotBlank(message = "Background check result is required")
    @Pattern(regexp = "^(passed|failed|pending)$", message = "Result must be passed, failed, or pending")
    @Schema(description = "Background check result", example = "passed")
    private String result;

    @Size(max = 1000, message = "Details must not exceed 1000 characters")
    @Schema(description = "Background check details", example = "No criminal record found")
    private String details;

    @Size(max = 100, message = "Conducted by must not exceed 100 characters")
    @Schema(description = "Person/agency who conducted the check", example = "Admin Team")
    private String conductedBy;
}