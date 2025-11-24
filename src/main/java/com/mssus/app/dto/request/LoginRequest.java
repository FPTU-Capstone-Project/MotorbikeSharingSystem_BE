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
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Schema(description = "Email associated with the account", example = "vo.van.f@student.hcmut.edu.vn")
    private String email;

    @Pattern(regexp = "^(rider|driver)$", message = "Target profile must be either 'rider' or 'driver'")
    @Schema(description = "Profile for the account to login as", example = "rider", defaultValue = "rider")
    @Builder.Default
    private String targetProfile = "rider";

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "Password1!")
    private String password;
}
