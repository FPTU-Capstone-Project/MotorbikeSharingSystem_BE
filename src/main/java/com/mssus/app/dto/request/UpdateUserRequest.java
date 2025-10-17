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
@Schema(description = "Update user request (Admin only)")
public class UpdateUserRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "Nguyen Van A")
    private String fullName;

    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "user@example.com")
    private String email;

    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Phone number must be valid Vietnamese format")
    @Schema(description = "Phone number", example = "0901234567")
    private String phone;

    @Pattern(regexp = "^(USER|ADMIN)$", message = "User type must be either 'USER' or 'ADMIN'")
    @Schema(description = "User type", example = "USER")
    private String userType;

    @Schema(description = "Student ID (optional)", example = "SE123456")
    private String studentId;

    @Schema(description = "Date of birth (optional)", example = "2000-01-01")
    private String dateOfBirth;

    @Pattern(regexp = "^(MALE|FEMALE|OTHER)$", message = "Gender must be MALE, FEMALE, or OTHER")
    @Schema(description = "Gender", example = "MALE")
    private String gender;

    @Pattern(regexp = "^(PENDING|ACTIVE|SUSPENDED)$", message = "Status must be PENDING, ACTIVE, or SUSPENDED")
    @Schema(description = "User status", example = "ACTIVE")
    private String status;

    @Schema(description = "Email verification status", example = "true")
    private Boolean emailVerified;

    @Schema(description = "Phone verification status", example = "true")
    private Boolean phoneVerified;
}
