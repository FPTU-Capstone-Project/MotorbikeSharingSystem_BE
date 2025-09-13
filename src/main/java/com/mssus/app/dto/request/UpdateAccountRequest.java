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
@Schema(description = "Update account request (Admin)")
public class UpdateAccountRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "Nguyen Van A")
    private String fullName;

    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "student@example.edu")
    private String email;

    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Phone number must be valid Vietnamese format")
    @Schema(description = "Phone number", example = "0901234567")
    private String phone;

    @Pattern(regexp = "^(student|admin)$", message = "User type must be either 'student' or 'admin'")
    @Schema(description = "User type", example = "student")
    private String userType;

    @Schema(description = "Whether the account is active", example = "true")
    private Boolean isActive;
}
