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
@Schema(description = "Login response")
public class LoginResponse {

    @JsonProperty("user_id")
    @Schema(description = "User ID", example = "123")
    private Integer userId;

    @JsonProperty("user_type")
    @Schema(description = "User type", example = "driver")
    private String userType;

    @Schema(description = "JWT access token")
    private String token;

    @JsonProperty("refresh_token")
    @Schema(description = "JWT refresh token")
    private String refreshToken;

    @JsonProperty("expires_in")
    @Schema(description = "Token expiration time in seconds", example = "3600")
    private Long expiresIn;
}
