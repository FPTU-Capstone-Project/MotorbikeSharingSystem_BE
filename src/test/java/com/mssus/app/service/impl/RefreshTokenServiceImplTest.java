package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RefreshToken;
import com.mssus.app.entity.User;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RefreshTokenRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.appconfig.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = createUser("user@example.com", UserStatus.ACTIVE, 1);
    }

    @Test
    void should_returnToken_when_generateRefreshTokenSuccess() {
        doNothing().when(refreshTokenRepository).deleteByUser(activeUser);
        doReturn("refresh-token").when(jwtService).generateRefreshToken(activeUser.getEmail(), activeUser.getTokenVersion());

        String token = refreshTokenService.generateRefreshToken(activeUser);

        assertThat(token).isEqualTo("refresh-token");

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).deleteByUser(activeUser);
        verify(jwtService).generateRefreshToken(activeUser.getEmail(), activeUser.getTokenVersion());
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedToken = refreshTokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(activeUser);
        assertThat(savedToken.getToken()).isEqualTo("refresh-token");
        assertThat(savedToken.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));

        verifyNoMoreInteractions(refreshTokenRepository, jwtService);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_throwRuntimeException_when_generateRefreshTokenFails() {
        doNothing().when(refreshTokenRepository).deleteByUser(activeUser);
        doThrow(new IllegalStateException("sign fail"))
            .when(jwtService).generateRefreshToken(activeUser.getEmail(), activeUser.getTokenVersion());

        assertThatThrownBy(() -> refreshTokenService.generateRefreshToken(activeUser))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to generate refresh token")
            .hasRootCauseInstanceOf(IllegalStateException.class);

        verify(refreshTokenRepository).deleteByUser(activeUser);
        verify(jwtService).generateRefreshToken(activeUser.getEmail(), activeUser.getTokenVersion());
        verifyNoMoreInteractions(refreshTokenRepository, jwtService);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_returnFalse_when_validateRefreshTokenJwtInvalid() {
        doReturn(false).when(jwtService).validateToken("invalid");

        boolean result = refreshTokenService.validateRefreshToken("invalid");

        assertThat(result).isFalse();
        verify(jwtService).validateToken("invalid");
        verifyNoInteractions(refreshTokenRepository, userRepository, driverProfileRepository);
    }

    @Test
    void should_returnFalse_when_validateRefreshTokenNotFound() {
        doReturn(true).when(jwtService).validateToken("token");
        doReturn(Optional.empty()).when(refreshTokenRepository).findByToken("token");

        boolean result = refreshTokenService.validateRefreshToken("token");

        assertThat(result).isFalse();
        verify(jwtService).validateToken("token");
        verify(refreshTokenRepository).findByToken("token");
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_returnFalse_when_validateRefreshTokenExpired() {
        RefreshToken expiredToken = createRefreshToken(activeUser, "expired", LocalDateTime.now().minusMinutes(5));
        doReturn(true).when(jwtService).validateToken("expired");
        doReturn(Optional.of(expiredToken)).when(refreshTokenRepository).findByToken("expired");

        boolean result = refreshTokenService.validateRefreshToken("expired");

        assertThat(result).isFalse();
        verify(jwtService).validateToken("expired");
        verify(refreshTokenRepository).findByToken("expired");
        verify(refreshTokenRepository).delete(expiredToken);
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    static Stream<Arguments> inactiveStatusProvider() {
        return Stream.of(
            Arguments.of(null, "null status"),
            Arguments.of(UserStatus.SUSPENDED, "suspended user"),
            Arguments.of(UserStatus.DELETED, "deleted user")
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("inactiveStatusProvider")
    void should_returnFalse_when_validateRefreshTokenUserNotActive(UserStatus status, String description) {
        User user = createUser("inactive@example.com", status, 2);
        RefreshToken refreshToken = createRefreshToken(user, "t", LocalDateTime.now().plusMinutes(30));
        doReturn(true).when(jwtService).validateToken("t");
        doReturn(Optional.of(refreshToken)).when(refreshTokenRepository).findByToken("t");

        boolean result = refreshTokenService.validateRefreshToken("t");

        assertThat(result).as(description).isFalse();
        verify(jwtService).validateToken("t");
        verify(refreshTokenRepository).findByToken("t");
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_returnTrue_when_validateRefreshTokenSuccess() {
        RefreshToken refreshToken = createRefreshToken(activeUser, "valid", LocalDateTime.now().plusMinutes(30));
        doReturn(true).when(jwtService).validateToken("valid");
        doReturn(Optional.of(refreshToken)).when(refreshTokenRepository).findByToken("valid");

        boolean result = refreshTokenService.validateRefreshToken("valid");

        assertThat(result).isTrue();
        verify(jwtService).validateToken("valid");
        verify(refreshTokenRepository).findByToken("valid");
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_updateUserAndDriver_when_invalidateRefreshTokenFound() {
        DriverProfile driverProfile = new DriverProfile();
        driverProfile.setStatus(DriverProfileStatus.ACTIVE);
        activeUser.setDriverProfile(driverProfile);
        RefreshToken refreshToken = createRefreshToken(activeUser, "token", LocalDateTime.now().plusDays(1));

        doReturn(Optional.of(refreshToken)).when(refreshTokenRepository).findByToken("token");
        doReturn(Optional.of(activeUser)).when(userRepository).findByEmail(activeUser.getEmail());

        refreshTokenService.invalidateRefreshToken("token");

        verify(refreshTokenRepository).findByToken("token");
        verify(refreshTokenRepository).delete(refreshToken);
        verify(userRepository).findByEmail(activeUser.getEmail());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getTokenVersion()).isEqualTo(2);

        verify(driverProfileRepository).save(driverProfile);
        assertThat(driverProfile.getStatus()).isEqualTo(DriverProfileStatus.INACTIVE);

        verifyNoMoreInteractions(refreshTokenRepository, userRepository, driverProfileRepository);
    }

    @Test
    void should_doNothing_when_invalidateRefreshTokenNotFound() {
        doReturn(Optional.empty()).when(refreshTokenRepository).findByToken("missing");

        refreshTokenService.invalidateRefreshToken("missing");

        verify(refreshTokenRepository).findByToken("missing");
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_returnUserId_when_getUserIdFromRefreshTokenValid() {
        RefreshToken refreshToken = createRefreshToken(activeUser, "token", LocalDateTime.now().plusMinutes(30));
        doReturn(true).when(jwtService).validateToken("token");
        doReturn(Optional.of(refreshToken)).when(refreshTokenRepository).findByToken("token");

        String userId = refreshTokenService.getUserIdFromRefreshToken("token");

        assertThat(userId).isEqualTo(activeUser.getUserId().toString());
        verify(jwtService).validateToken("token");
        verify(refreshTokenRepository, times(2)).findByToken("token");
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(userRepository, driverProfileRepository);
    }

    @Test
    void should_returnNull_when_getUserIdFromRefreshTokenInvalid() {
        doReturn(false).when(jwtService).validateToken("bad");

        String userId = refreshTokenService.getUserIdFromRefreshToken("bad");

        assertThat(userId).isNull();
        verify(jwtService).validateToken("bad");
        verifyNoInteractions(refreshTokenRepository, userRepository, driverProfileRepository);
    }

    @Test
    void should_deleteExpiredTokens_when_cleanupExpiredTokensCalled() {
        LocalDateTime now = LocalDateTime.now();

        refreshTokenService.cleanupExpiredTokens();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokenRepository).deleteExpiredTokens(captor.capture());
        assertThat(captor.getValue()).isAfterOrEqualTo(now);
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(jwtService, userRepository, driverProfileRepository);
    }

    @Test
    void should_deleteExistingTokens_when_deleteExistingRefreshTokensForUser() {
        RefreshTokenServiceImpl spyService = spy(refreshTokenService);
        doNothing().when(refreshTokenRepository).deleteByUser(activeUser);

        spyService.deleteExistingRefreshTokensForUser(activeUser);

        verify(refreshTokenRepository).deleteByUser(activeUser);
        verifyNoMoreInteractions(refreshTokenRepository);
        verifyNoInteractions(jwtService, userRepository, driverProfileRepository);
    }

    private static User createUser(String email, UserStatus status, int version) {
        User user = new User();
        user.setUserId(100);
        user.setEmail(email);
        user.setStatus(status);
        user.setTokenVersion(version);
        return user;
    }

    private static RefreshToken createRefreshToken(User user, String token, LocalDateTime expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(token);
        refreshToken.setExpiresAt(expiresAt);
        return refreshToken;
    }
}

