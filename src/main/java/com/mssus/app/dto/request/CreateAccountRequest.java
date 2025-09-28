package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
@Schema(description = "Create account request (Admin)")
public class CreateAccountRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "Nguyen Van A")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "student@example.edu")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Phone number must be valid Vietnamese format")
    @Schema(description = "Phone number", example = "0901234567")
    private String phone;

    @NotBlank(message = "User type is required")
    @Pattern(regexp = "^(user|admin)$", message = "User type must be either 'user' or 'admin'")
    @Schema(description = "User type", example = "user")
    private String userType;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$", 
             message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
    @Schema(description = "Initial password")
    private String password;
}
