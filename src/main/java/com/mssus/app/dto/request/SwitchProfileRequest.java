package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Switch profile request")
public class SwitchProfileRequest {

    @NotBlank(message = "Target role is required")
    @Pattern(regexp = "^(rider|driver)$", message = "Target role must be either 'rider' or 'driver'")
    @Schema(description = "Target role to switch to", example = "driver")
    private String targetRole;
}
