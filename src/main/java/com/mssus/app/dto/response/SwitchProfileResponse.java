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
@Schema(description = "Switch profile response")
public class SwitchProfileResponse {
    @Schema(description = "Currently active profile", example = "rider")
    @JsonProperty("active_profile")
    private String activeProfile;

    @Schema(description = "New JWT access token for the switched profile")
    @JsonProperty("access_token")
    private String accessToken;
}
