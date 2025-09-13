package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OTP response")
public class OtpResponse {

    @Schema(description = "Response message", example = "OTP sent successfully")
    private String message;

    @JsonProperty("otp_for")
    @Schema(description = "OTP purpose", example = "VERIFY_MAIL")
    private String otpFor;

    @JsonProperty("verified_field")
    @Schema(description = "Field that was verified (for verification success)", example = "email")
    private String verifiedField;
}
