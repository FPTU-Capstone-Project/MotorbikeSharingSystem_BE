package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Email or phone is required")
    @Schema(description = "Email or phone number associated with the account", example = "student@example.edu")
    private String emailOrPhone;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password")
    private String password;
}
