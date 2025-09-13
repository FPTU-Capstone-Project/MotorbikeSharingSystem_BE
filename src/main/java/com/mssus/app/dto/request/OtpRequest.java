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
@Schema(description = "OTP submission request")
public class OtpRequest {

    @NotBlank(message = "OTP purpose is required")
    @Pattern(regexp = "^(FORGOT_PASSWORD|VERIFY_MAIL|VERIFY_PHONE)$", 
             message = "OTP purpose must be FORGOT_PASSWORD, VERIFY_MAIL, or VERIFY_PHONE")
    @Schema(description = "The OTP purpose", example = "VERIFY_MAIL")
    private String otpFor;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP code must be 6 digits")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP code must be 6 digits")
    @Schema(description = "The one-time password received by the user", example = "123456")
    private String code;

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$", 
             message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    @Schema(description = "New password (required when otpFor = FORGOT_PASSWORD)")
    private String newPassword;
}
