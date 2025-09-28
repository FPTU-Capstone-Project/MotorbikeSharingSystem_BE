package com.mssus.app.service;

import com.mssus.app.entity.User;

public interface RefreshTokenService {
    String generateRefreshToken(User user);

    boolean validateRefreshToken(String token);

    void invalidateRefreshToken(String token);

    String getUserIdFromRefreshToken(String token);
}
