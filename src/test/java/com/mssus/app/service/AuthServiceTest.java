package com.mssus.app.service;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.*;
import com.mssus.app.repository.*;
import com.mssus.app.security.JwtService;
import com.mssus.app.service.RefreshTokenService;
import com.mssus.app.service.impl.AuthServiceImpl;
import com.mssus.app.util.Constants;
import com.mssus.app.util.OtpUtil;
import com.mssus.app.util.ValidationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RiderProfileRepository riderProfileRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User testUser;
    private RiderProfile testRiderProfile;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        // Clear static context before each test
        AuthServiceImpl.userContext.clear();

        // Setup test data
        registerRequest = RegisterRequest.builder()
                .fullName("Test User")
                .email("test@example.com")
                .phone("0901234567")
                .password("TestPass123")
                .role("rider")
                .build();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("TestPass123")
                .targetProfile("rider")
                .build();

        testUser = User.builder()
                .userId(1)
                .email("test@example.com")
                .phone("0901234567")
                .passwordHash("hashedPassword")
                .fullName("Test User")
                .userType(UserType.USER)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .phoneVerified(true)
                .tokenVersion(1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testRiderProfile = RiderProfile.builder()
                .riderId(1)
                .user(testUser)
                .totalRides(0)
                .totalSpent(BigDecimal.ZERO)
                .preferredPaymentMethod(PaymentMethod.WALLET)
                .status(RiderProfileStatus.ACTIVE)
                .build();

        testWallet = Wallet.builder()
                .walletId(1)
                .user(testUser)
                .shadowBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .isActive(true)
                .build();

        testUser.setRiderProfile(testRiderProfile);
        testUser.setWallet(testWallet);
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("register_ValidRequest_ReturnsRegisterResponse")
        void register_ValidRequest_ReturnsRegisterResponse() {
            // Arrange
            when(userRepository.existsByEmailAndStatusNot(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByEmailAndStatus(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(riderProfileRepository.save(any(RiderProfile.class))).thenReturn(testRiderProfile);
            when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("jwt-token");

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");

                // Act
                RegisterResponse response = authService.register(registerRequest);

                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getUserId()).isEqualTo(1);
                assertThat(response.getEmail()).isEqualTo("test@example.com");
                assertThat(response.getPhone()).isEqualTo("0901234567");
                assertThat(response.getFullName()).isEqualTo("Test User");
                assertThat(response.getToken()).isEqualTo("jwt-token");
                assertThat(response.getUserType()).isEqualTo("rider");

                // Verify interactions
                verify(userRepository).existsByEmailAndStatusNot("test@example.com", UserStatus.EMAIL_VERIFYING);
                verify(userRepository).existsByEmailAndStatus("test@example.com", UserStatus.EMAIL_VERIFYING);
                verify(userRepository).existsByPhone("0901234567");
                verify(passwordEncoder).encode("TestPass123");
                verify(userRepository).save(any(User.class));
                verify(riderProfileRepository).save(any(RiderProfile.class));
                verify(walletRepository).save(any(Wallet.class));
                verify(jwtService).generateToken(eq("test@example.com"), any(Map.class));
            }
        }

        @Test
        @DisplayName("register_EmailAlreadyExists_ThrowsConflictException")
        void register_EmailAlreadyExists_ThrowsConflictException() {
            // Arrange
            when(userRepository.existsByEmailAndStatusNot(anyString(), any(UserStatus.class))).thenReturn(true);

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");

                // Act & Assert
                assertThatThrownBy(() -> authService.register(registerRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.conflict.email-exists");

                verify(userRepository, never()).save(any(User.class));
            }
        }

        @Test
        @DisplayName("register_EmailPendingVerification_ThrowsConflictException")
        void register_EmailPendingVerification_ThrowsConflictException() {
            // Arrange
            when(userRepository.existsByEmailAndStatusNot(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByEmailAndStatus(anyString(), any(UserStatus.class))).thenReturn(true);

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");

                // Act & Assert
                assertThatThrownBy(() -> authService.register(registerRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.conflict.email-exists");

                verify(userRepository, never()).save(any(User.class));
            }
        }

        @Test
        @DisplayName("register_PhoneAlreadyExists_ThrowsConflictException")
        void register_PhoneAlreadyExists_ThrowsConflictException() {
            // Arrange
            when(userRepository.existsByEmailAndStatusNot(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByEmailAndStatus(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(true);

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");

                // Act & Assert
                assertThatThrownBy(() -> authService.register(registerRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.conflict.phone-exists");

                verify(userRepository, never()).save(any(User.class));
            }
        }

        @Test
        @DisplayName("register_DriverRole_CreatesRiderProfile")
        void register_DriverRole_CreatesRiderProfile() {
            // Arrange
            registerRequest.setRole("driver");
            when(userRepository.existsByEmailAndStatusNot(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByEmailAndStatus(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(riderProfileRepository.save(any(RiderProfile.class))).thenReturn(testRiderProfile);
            when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("jwt-token");

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");

                // Act
                RegisterResponse response = authService.register(registerRequest);

                // Assert
                assertThat(response).isNotNull();
                verify(riderProfileRepository).save(any(RiderProfile.class));
            }
        }

        @Test
        @DisplayName("register_NullRole_CreatesRiderProfile")
        void register_NullRole_CreatesRiderProfile() {
            // Arrange
            registerRequest.setRole(null);
            when(userRepository.existsByEmailAndStatusNot(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByEmailAndStatus(anyString(), any(UserStatus.class))).thenReturn(false);
            when(userRepository.existsByPhone(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(riderProfileRepository.save(any(RiderProfile.class))).thenReturn(testRiderProfile);
            when(walletRepository.save(any(Wallet.class))).thenReturn(testWallet);
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("jwt-token");

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");

                // Act
                RegisterResponse response = authService.register(registerRequest);

                // Assert
                assertThat(response).isNotNull();
                verify(riderProfileRepository).save(any(RiderProfile.class));
            }
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("login_ValidCredentials_ReturnsLoginResponse")
        void login_ValidCredentials_ReturnsLoginResponse() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("access-token");
            when(jwtService.getExpirationTime()).thenReturn(3600000L);
            when(refreshTokenService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act
                LoginResponse response = authService.login(loginRequest);

                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getUserId()).isEqualTo(1);
                assertThat(response.getUserType()).isEqualTo("USER");
                assertThat(response.getActiveProfile()).isEqualTo("rider");
                assertThat(response.getAccessToken()).isEqualTo("access-token");
                assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
                assertThat(response.getExpiresIn()).isEqualTo(3600L);

                verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
                verify(jwtService).generateToken(eq("test@example.com"), any(Map.class));
                verify(refreshTokenService).generateRefreshToken(testUser);
            }
        }

        @Test
        @DisplayName("login_UserNotFound_ThrowsNotFoundException")
        void login_UserNotFound_ThrowsNotFoundException() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.not-found.by-email");

                verify(authenticationManager, never()).authenticate(any());
            }
        }

        @Test
        @DisplayName("login_InvalidEmail_ThrowsValidationException")
        void login_InvalidEmail_ThrowsValidationException() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(false);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.validation.invalid-email");

                verify(authenticationManager, never()).authenticate(any());
            }
        }

        @Test
        @DisplayName("login_UserDoesNotHaveProfile_ThrowsValidationException")
        void login_UserDoesNotHaveProfile_ThrowsValidationException() {
            // Arrange
            testUser.setRiderProfile(null);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.validation.profile-not-exists");

                verify(authenticationManager, never()).authenticate(any());
            }
        }

        @Test
        @DisplayName("login_AdminUser_DoesNotRequireProfile")
        void login_AdminUser_DoesNotRequireProfile() {
            // Arrange
            testUser.setUserType(UserType.ADMIN);
            testUser.setRiderProfile(null);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("access-token");
            when(jwtService.getExpirationTime()).thenReturn(3600000L);
            when(refreshTokenService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act
                LoginResponse response = authService.login(loginRequest);

                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getUserType()).isEqualTo("ADMIN");
                assertThat(response.getActiveProfile()).isNull();
                verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
            }
        }

        @Test
        @DisplayName("login_UserSuspended_ThrowsUnauthorizedException")
        void login_UserSuspended_ThrowsUnauthorizedException() {
            // Arrange
            testUser.setStatus(UserStatus.SUSPENDED);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("auth.unauthorized.account-suspended");

                verify(authenticationManager, never()).authenticate(any());
            }
        }

        @Test
        @DisplayName("login_UserPending_ThrowsUnauthorizedException")
        void login_UserPending_ThrowsUnauthorizedException() {
            // Arrange
            testUser.setStatus(UserStatus.PENDING);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("auth.unauthorized.account-pending");

                verify(authenticationManager, never()).authenticate(any());
            }
        }

        @Test
        @DisplayName("login_UserEmailVerifying_ThrowsUnauthorizedException")
        void login_UserEmailVerifying_ThrowsUnauthorizedException() {
            // Arrange
            testUser.setStatus(UserStatus.EMAIL_VERIFYING);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("auth.unauthorized.email-verification-pending");

                verify(authenticationManager, never()).authenticate(any());
            }
        }

        @Test
        @DisplayName("login_AuthenticationFails_ThrowsBadCredentialsException")
        void login_AuthenticationFails_ThrowsBadCredentialsException() {
            // Arrange
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Invalid credentials"));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act & Assert
                assertThatThrownBy(() -> authService.login(loginRequest))
                        .isInstanceOf(BadCredentialsException.class)
                        .hasMessageContaining("Invalid credentials");
            }
        }
    }

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("logout_ValidRefreshToken_ReturnsSuccessMessage")
        void logout_ValidRefreshToken_ReturnsSuccessMessage() {
            // Arrange
            String refreshToken = "valid-refresh-token";

            // Act
            MessageResponse response = authService.logout(refreshToken);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getMessage()).isEqualTo("Logged out successfully");
            verify(refreshTokenService).invalidateRefreshToken(refreshToken);
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("refreshToken_ValidRefreshToken_ReturnsNewAccessToken")
        void refreshToken_ValidRefreshToken_ReturnsNewAccessToken() {
            // Arrange
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            Map<String, Object> claims = new HashMap<>();
            claims.put("iss", "mssus.api");
            claims.put("sub", "user-1");
            claims.put("email", "test@example.com");

            when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(true);
            when(refreshTokenService.getUserIdFromRefreshToken(anyString())).thenReturn("1");
            when(userRepository.findById(anyInt())).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("new-access-token");

            // Add claims to user context
            AuthServiceImpl.userContext.put("1", claims);

            // Act
            TokenRefreshResponse response = authService.refreshToken(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            verify(refreshTokenService).validateRefreshToken("valid-refresh-token");
            verify(refreshTokenService).getUserIdFromRefreshToken("valid-refresh-token");
            verify(userRepository).findById(1);
            verify(jwtService).generateToken("test@example.com", claims);
        }

        @Test
        @DisplayName("refreshToken_InvalidRefreshToken_ThrowsUnauthorizedException")
        void refreshToken_InvalidRefreshToken_ThrowsUnauthorizedException() {
            // Arrange
            TokenRefreshRequest request = new TokenRefreshRequest("invalid-refresh-token");
            when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("auth.unauthorized.invalid-refresh-token");

            verify(refreshTokenService, never()).getUserIdFromRefreshToken(anyString());
        }

        @Test
        @DisplayName("refreshToken_NullUserId_ThrowsUnauthorizedException")
        void refreshToken_NullUserId_ThrowsUnauthorizedException() {
            // Arrange
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(true);
            when(refreshTokenService.getUserIdFromRefreshToken(anyString())).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("auth.unauthorized.invalid-refresh-token");

            verify(userRepository, never()).findById(anyInt());
        }

        @Test
        @DisplayName("refreshToken_UserNotFound_ThrowsNotFoundException")
        void refreshToken_UserNotFound_ThrowsNotFoundException() {
            // Arrange
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(true);
            when(refreshTokenService.getUserIdFromRefreshToken(anyString())).thenReturn("999");
            when(userRepository.findById(anyInt())).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BaseDomainException.class)
                        .extracting("errorId")
                        .isEqualTo("user.not-found.by-id");
        }

        @Test
        @DisplayName("refreshToken_UserSuspended_ThrowsUnauthorizedException")
        void refreshToken_UserSuspended_ThrowsUnauthorizedException() {
            // Arrange
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            testUser.setStatus(UserStatus.SUSPENDED);
            when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(true);
            when(refreshTokenService.getUserIdFromRefreshToken(anyString())).thenReturn("1");
            when(userRepository.findById(anyInt())).thenReturn(Optional.of(testUser));

            // Act & Assert
            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(BaseDomainException.class)
                    .extracting("errorId")
                    .isEqualTo("auth.unauthorized.account-suspended");
        }

        @Test
        @DisplayName("refreshToken_NoUserContext_BuildsNewClaims")
        void refreshToken_NoUserContext_BuildsNewClaims() {
            // Arrange
            TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");
            when(refreshTokenService.validateRefreshToken(anyString())).thenReturn(true);
            when(refreshTokenService.getUserIdFromRefreshToken(anyString())).thenReturn("1");
            when(userRepository.findById(anyInt())).thenReturn(Optional.of(testUser));
            when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("new-access-token");

            // Act
            TokenRefreshResponse response = authService.refreshToken(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            verify(jwtService).generateToken(eq("test@example.com"), any(Map.class));
        }
    }

    @Nested
    @DisplayName("Forgot Password Tests")
    class ForgotPasswordTests {

        @Test
        @DisplayName("forgotPassword_ValidEmail_ReturnsSuccessMessage")
        void forgotPassword_ValidEmail_ReturnsSuccessMessage() {
            // Arrange
            ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class);
                 MockedStatic<OtpUtil> otpUtil = mockStatic(OtpUtil.class)) {

                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);
                otpUtil.when(OtpUtil::generateOtp).thenReturn("123456");

                // Act
                MessageResponse response = authService.forgotPassword(request);

                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getMessage()).isEqualTo("OTP sent to your registered contact");
                verify(userRepository).findByEmail("test@example.com");
                otpUtil.verify(() -> OtpUtil.generateOtp());
                otpUtil.verify(() -> OtpUtil.storeOtp("test@example.com:" + Constants.OTP_FORGOT_PASSWORD, "123456", OtpFor.FORGOT_PASSWORD));
            }
        }

        @Test
        @DisplayName("forgotPassword_ValidPhone_ReturnsSuccessMessage")
        void forgotPassword_ValidPhone_ReturnsSuccessMessage() {
            // Arrange
            ForgotPasswordRequest request = new ForgotPasswordRequest("0901234567");
            when(userRepository.findByPhone(anyString())).thenReturn(Optional.of(testUser));

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class);
                 MockedStatic<OtpUtil> otpUtil = mockStatic(OtpUtil.class)) {

                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(false);
                validationUtil.when(() -> ValidationUtil.normalizePhone(anyString())).thenReturn("0901234567");
                otpUtil.when(OtpUtil::generateOtp).thenReturn("123456");

                // Act
                MessageResponse response = authService.forgotPassword(request);

                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getMessage()).isEqualTo("OTP sent to your registered contact");
                verify(userRepository).findByPhone("0901234567");
            }
        }

        @Test
        @DisplayName("forgotPassword_UserNotFound_ReturnsSuccessMessage")
        void forgotPassword_UserNotFound_ReturnsSuccessMessage() {
            // Arrange
            ForgotPasswordRequest request = new ForgotPasswordRequest("nonexistent@example.com");
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            try (MockedStatic<ValidationUtil> validationUtil = mockStatic(ValidationUtil.class)) {
                validationUtil.when(() -> ValidationUtil.isValidEmail(anyString())).thenReturn(true);

                // Act
                MessageResponse response = authService.forgotPassword(request);

                // Assert
                assertThat(response).isNotNull();
                assertThat(response.getMessage()).isEqualTo("OTP sent to your registered contact");
                verify(userRepository).findByEmail("nonexistent@example.com");
            }
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("buildTokenClaims_UserWithRiderProfile_ReturnsCorrectClaims")
        void buildTokenClaims_UserWithRiderProfile_ReturnsCorrectClaims() {
            // Act
            Map<String, Object> claims = authService.buildTokenClaims(testUser, "rider");

            // Assert
            assertThat(claims).isNotNull();
            assertThat(claims.get("iss")).isEqualTo("mssus.api");
            assertThat(claims.get("sub")).isEqualTo("user-1");
            assertThat(claims.get("email")).isEqualTo("test@example.com");
            assertThat(claims.get("active_profile")).isEqualTo("rider");
            assertThat(claims.get("token_version")).isEqualTo(1);

            @SuppressWarnings("unchecked")
            List<String> profiles = (List<String>) claims.get("profiles");
            assertThat(profiles).contains("RIDER");

            @SuppressWarnings("unchecked")
            Map<String, String> profileStatus = (Map<String, String>) claims.get("profile_status");
            assertThat(profileStatus.get("RIDER")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("getUserProfiles_UserWithRiderProfile_ReturnsRiderProfile")
        void getUserProfiles_UserWithRiderProfile_ReturnsRiderProfile() {
            // Act
            List<String> profiles = authService.getUserProfiles(testUser);

            // Assert
            assertThat(profiles).isNotNull();
            assertThat(profiles).containsExactly("RIDER");
        }

        @Test
        @DisplayName("getUserProfiles_UserWithDriverProfile_ReturnsDriverProfile")
        void getUserProfiles_UserWithDriverProfile_ReturnsDriverProfile() {
            // Arrange
            DriverProfile driverProfile = DriverProfile.builder()
                    .driverId(1)
                    .user(testUser)
                    .status(DriverProfileStatus.ACTIVE)
                    .build();
            testUser.setDriverProfile(driverProfile);

            // Act
            List<String> profiles = authService.getUserProfiles(testUser);

            // Assert
            assertThat(profiles).isNotNull();
            assertThat(profiles).containsExactlyInAnyOrder("RIDER", "DRIVER");
        }

        @Test
        @DisplayName("buildProfileStatus_UserWithRiderProfile_ReturnsCorrectStatus")
        void buildProfileStatus_UserWithRiderProfile_ReturnsCorrectStatus() {
            // Act
            Map<String, String> profileStatus = authService.buildProfileStatus(testUser);

            // Assert
            assertThat(profileStatus).isNotNull();
            assertThat(profileStatus.get("RIDER")).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("validateUserBeforeGrantingToken_ActiveUser_DoesNotThrowException")
        void validateUserBeforeGrantingToken_ActiveUser_DoesNotThrowException() {
            // Act & Assert
            assertThatCode(() -> authService.validateUserBeforeGrantingToken(testUser))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validateUserBeforeGrantingToken_SuspendedUser_ThrowsException")
        void validateUserBeforeGrantingToken_SuspendedUser_ThrowsException() {
            // Arrange
            testUser.setStatus(UserStatus.SUSPENDED);

            // Act & Assert
            assertThatThrownBy(() -> authService.validateUserBeforeGrantingToken(testUser))
                    .isInstanceOf(BaseDomainException.class)
                    .extracting("errorId")
                    .isEqualTo("auth.unauthorized.account-suspended");
        }

        @Test
        @DisplayName("validateUserBeforeGrantingToken_PendingUser_ThrowsException")
        void validateUserBeforeGrantingToken_PendingUser_ThrowsException() {
            // Arrange
            testUser.setStatus(UserStatus.PENDING);

            // Act & Assert
            assertThatThrownBy(() -> authService.validateUserBeforeGrantingToken(testUser))
                    .isInstanceOf(BaseDomainException.class)
                    .extracting("errorId")
                    .isEqualTo("auth.unauthorized.account-pending");
        }

        @Test
        @DisplayName("validateUserBeforeGrantingToken_EmailVerifyingUser_ThrowsException")
        void validateUserBeforeGrantingToken_EmailVerifyingUser_ThrowsException() {
            // Arrange
            testUser.setStatus(UserStatus.EMAIL_VERIFYING);

            // Act & Assert
            assertThatThrownBy(() -> authService.validateUserBeforeGrantingToken(testUser))
                    .isInstanceOf(BaseDomainException.class)
                    .extracting("errorId")
                    .isEqualTo("auth.unauthorized.email-verification-pending");
        }
    }
}
