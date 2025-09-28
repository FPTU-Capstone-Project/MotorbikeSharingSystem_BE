package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Get OTP request")
public class GetOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Unique email address used for identifying and verifying OTP code", example = "student@example.edu")
    private String email;

    @NotBlank(message = "OTP purpose is required")
    @Pattern(regexp = "^(FORGOT_PASSWORD|VERIFY_EMAIL|VERIFY_PHONE)$",
        message = "OTP purpose must be FORGOT_PASSWORD, VERIFY_EMAIL, or VERIFY_PHONE")
    @Schema(description = "The OTP purpose", example = "VERIFY_EMAIL")
    private String otpFor;
}
