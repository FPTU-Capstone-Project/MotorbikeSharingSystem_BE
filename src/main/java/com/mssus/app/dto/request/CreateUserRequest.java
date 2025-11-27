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
@Schema(description = "Create user request (Admin only) - only email/userType required")
public class CreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Unique email address", example = "user@example.com")
    private String email;

    @Pattern(regexp = "^(USER|ADMIN)$", message = "User type must be either 'USER' or 'ADMIN'")
    @Schema(description = "User type", example = "USER")
    private String userType;
}
