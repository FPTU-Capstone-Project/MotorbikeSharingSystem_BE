package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RefreshToken;
import com.mssus.app.entity.User;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RefreshTokenRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.infrastructure.security.JwtService;
import com.mssus.app.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    
    @Override
    @Transactional
    public String generateRefreshToken(User user) {
        try {
            deleteExistingRefreshTokensForUser(user);

            String tokenValue = jwtService.generateRefreshToken(user.getEmail(), user.getTokenVersion());

            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setUser(user);
            refreshToken.setToken(tokenValue);
            refreshToken.setExpiresAt(LocalDateTime.now().plusDays(30));

            refreshTokenRepository.save(refreshToken);
            
            log.info("Generated new refresh token for user: {}", user.getEmail());
            return tokenValue;
            
        } catch (Exception e) {
            log.error("Error generating refresh token for user {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Failed to generate refresh token", e);
        }
    }

    @Override
    public boolean validateRefreshToken(String token) {
        try {
            if (!jwtService.validateToken(token)) {
                log.warn("Invalid JWT token structure");
                return false;
            }

            Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
            if (refreshTokenOpt.isEmpty()) {
                log.warn("Refresh token not found in database");
                return false;
            }
            
            RefreshToken refreshToken = refreshTokenOpt.get();
            

            if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Refresh token has expired for user: {}", refreshToken.getUser().getEmail());
                refreshTokenRepository.delete(refreshToken);
                return false;
            }

            if (refreshToken.getUser().getStatus() == null || 
                !UserStatus.ACTIVE.equals(refreshToken.getUser().getStatus())) {
                log.warn("User is not active for refresh token: {}", refreshToken.getUser().getEmail());
                return false;
            }
            
            log.debug("Refresh token validation successful for user: {}", refreshToken.getUser().getEmail());
            return true;
            
        } catch (Exception e) {
            log.error("Error validating refresh token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public void invalidateRefreshToken(String token) {
        try {
            Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
            if (refreshTokenOpt.isPresent()) {
                RefreshToken refreshToken = refreshTokenOpt.get();
                refreshTokenRepository.delete(refreshToken);
                userRepository.findByEmail(refreshToken.getUser().getEmail()).ifPresent(user -> {
                    user.incrementTokenVersion();
                    if (user.getDriverProfile() != null) {
                        DriverProfile driverProfile = user.getDriverProfile();
                        driverProfile.setStatus(DriverProfileStatus.INACTIVE);
                        driverProfileRepository.save(driverProfile);
                    }
                    userRepository.save(user);
                });
                log.info("Deleted refresh token for user: {}", refreshToken.getUser().getEmail());
            } else {
                log.warn("Attempted to delete non-existent refresh token");
            }
        } catch (Exception e) {
            log.error("Error deleting refresh token: {}", e.getMessage());
            throw new RuntimeException("Failed to delete refresh token", e);
        }
    }

    @Override
    public String getUserIdFromRefreshToken(String token) {
        try {
            if (!validateRefreshToken(token)) {
                log.warn("Cannot extract user ID from invalid refresh token");
                return null;
            }
            
            // Find the token in database
            Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);
            if (refreshTokenOpt.isPresent()) {
                RefreshToken refreshToken = refreshTokenOpt.get();
                String userId = refreshToken.getUser().getUserId().toString();
                log.debug("Extracted user ID {} from refresh token", userId);
                return userId;
            }
            
            log.warn("Refresh token not found when extracting user ID");
            return null;
            
        } catch (Exception e) {
            log.error("Error extracting user ID from refresh token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Helper method to delete existing refresh tokens for a user
     * This ensures only one refresh token exists per user at a time
     */
    @Transactional
    protected void deleteExistingRefreshTokensForUser(User user) {
        try {
            refreshTokenRepository.deleteByUser(user);
            log.debug("Cleaned up existing refresh tokens for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Error cleaning up existing refresh tokens for user {}: {}", user.getEmail(), e.getMessage());
        }
    }
    
    /**
     * Clean up expired refresh tokens from the database
     * This method can be called periodically to maintain database hygiene
     */
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());
            log.info("Cleaned up expired refresh tokens");
        } catch (Exception e) {
            log.error("Error cleaning up expired refresh tokens: {}", e.getMessage());
        }
    }
}
