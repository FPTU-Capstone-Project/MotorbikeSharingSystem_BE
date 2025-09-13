package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Registration response")
public class RegisterResponse {

    @JsonProperty("user_id")
    @Schema(description = "User ID", example = "123")
    private Integer userId;

    @JsonProperty("user_type")
    @Schema(description = "User type", example = "rider")
    private String userType;

    @Schema(description = "Email address", example = "student@example.edu")
    private String email;

    @Schema(description = "Phone number", example = "0901234567")
    private String phone;

    @JsonProperty("full_name")
    @Schema(description = "Full name", example = "Nguyen Van A")
    private String fullName;

    @Schema(description = "JWT access token")
    private String token;

    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(description = "Account creation timestamp", example = "2025-09-13T09:00:00Z")
    private LocalDateTime createdAt;
}
