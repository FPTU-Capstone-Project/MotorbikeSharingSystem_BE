package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Update profile request")
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "Nguyen Van A")
    private String fullName;

    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "student@example.edu")
    private String email;

    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Phone number must be valid Vietnamese format")
    @Schema(description = "Phone number", example = "0901234567")
    private String phone;

    @Pattern(regexp = "^(wallet|credit_card)$", message = "Payment method must be either 'wallet' or 'credit_card'")
    @Schema(description = "Preferred payment method", example = "wallet")
    private String preferredPaymentMethod;

    @Schema(description = "Emergency contact", example = "+84909999888")
    private String emergencyContact;

    @Size(min = 3, max = 32, message = "Student ID must be between 3 and 32 characters")
    @Schema(description = "Student ID (MSSV)", example = "SE123456")
    private String studentId;
}
