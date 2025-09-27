package com.mssus.app.dto.request;

import jakarta.validation.constraints.NotEmpty;

public record LogoutRequest(@NotEmpty(message = "Refresh token is required") String refreshToken) {
}
