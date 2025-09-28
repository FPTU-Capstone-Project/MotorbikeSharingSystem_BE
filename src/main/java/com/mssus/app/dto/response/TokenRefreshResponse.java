package com.mssus.app.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token refreshing response")
public class TokenRefreshResponse {
    @Schema(description = "New JWT access token")
    private String accessToken;
}
