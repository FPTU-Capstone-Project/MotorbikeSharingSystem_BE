package com.mssus.app.service.impl;

import com.mssus.app.common.enums.OtpFor;
import com.mssus.app.common.enums.PaymentMethod;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.GetOtpRequest;
import com.mssus.app.dto.request.OtpRequest;
import com.mssus.app.dto.response.OtpResponse;
import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.User;
import com.mssus.app.repository.RiderProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.EmailService;
import com.mssus.app.util.OtpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OtpServiceImpl Tests")
class OtpServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RiderProfileRepository riderProfileRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        // Setup is handled by @InjectMocks
    }

    // Helper methods
    private User createTestUser(Integer userId, String email, String fullName, UserStatus status, 
                               Boolean emailVerified, Boolean phoneVerified) {
        return User.builder()
                .userId(userId)
                .email(email)
                .phone("0123456789")
                .fullName(fullName)
                .studentId("ST001")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .gender("Male")
                .emailVerified(emailVerified)
                .phoneVerified(phoneVerified)
                .status(status)
                .tokenVersion(1)
                .build();
    }

    private GetOtpRequest createGetOtpRequest(String email, String otpFor) {
        return GetOtpRequest.builder()
                .email(email)
                .otpFor(otpFor)
                .build();
    }

    private OtpRequest createOtpRequest(String email, String otpFor, String code) {
        return OtpRequest.builder()
                .email(email)
                .otpFor(otpFor)
                .code(code)
                .build();
    }

    private RiderProfile createTestRiderProfile(User user) {
        return RiderProfile.builder()
                .user(user)
                .status(RiderProfileStatus.PENDING)
                .totalRides(0)
                .totalSpent(BigDecimal.ZERO)
                .preferredPaymentMethod(PaymentMethod.WALLET)
                .createdAt(LocalDateTime.now())
                .emergencyContact("113")
                .build();
    }

    // Tests for requestOtp method
    @Test
    @DisplayName("Should request OTP for email verification successfully")
    void should_requestOtpForEmailVerification_when_userInEmailVerifyingState() {
        // Arrange
        String email = "test@example.com";
        User user = createTestUser(1, email, "Test User", UserStatus.EMAIL_VERIFYING, false, false);
        GetOtpRequest request = createGetOtpRequest(email, "VERIFY_EMAIL");

        when(userRepository.findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.of(user));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(OtpUtil::generateOtp).thenReturn("123456");
            otpUtilMock.when(() -> OtpUtil.storeOtp(anyString(), anyString(), any(OtpFor.class))).thenAnswer(invocation -> null);

            // Act
            OtpResponse result = otpService.requestOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("OTP sent successfully");
            assertThat(result.getOtpFor()).isEqualTo("VERIFY_EMAIL");
            assertThat(result.getVerifiedField()).isNull();

            verify(userRepository).findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING);
            verify(emailService).sendEmail(eq(email), eq("Verify your email"), 
                    eq("emails/otp-email-verification"), anyMap(), eq(EmailPriority.HIGH), eq(1L), eq("email-verification"));
            otpUtilMock.verify(() -> OtpUtil.generateOtp());
            otpUtilMock.verify(() -> OtpUtil.storeOtp(eq(email + ":VERIFY_EMAIL"), eq("123456"), eq(OtpFor.VERIFY_EMAIL)));
        }
    }

    @Test
    @DisplayName("Should request OTP for phone verification successfully")
    void should_requestOtpForPhoneVerification_when_userNotInEmailVerifyingState() {
        // Arrange
        String email = "test@example.com";
        User user = createTestUser(1, email, "Test User", UserStatus.PENDING, true, false);
        GetOtpRequest request = createGetOtpRequest(email, "VERIFY_PHONE");

        when(userRepository.findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.of(user));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(OtpUtil::generateOtp).thenReturn("654321");
            otpUtilMock.when(() -> OtpUtil.storeOtp(anyString(), anyString(), any(OtpFor.class))).thenAnswer(invocation -> null);

            // Act
            OtpResponse result = otpService.requestOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("OTP sent successfully");
            assertThat(result.getOtpFor()).isEqualTo("VERIFY_PHONE");
            assertThat(result.getVerifiedField()).isNull();

            verify(userRepository).findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING);
            verify(emailService).sendEmail(eq(email), eq("Verify your phone"), 
                    eq("emails/otp-phone-verification"), anyMap(), eq(EmailPriority.HIGH), eq(1L), eq("phone-verification"));
            otpUtilMock.verify(() -> OtpUtil.generateOtp());
            otpUtilMock.verify(() -> OtpUtil.storeOtp(eq(email + ":VERIFY_PHONE"), eq("654321"), eq(OtpFor.VERIFY_PHONE)));
        }
    }

    @Test
    @DisplayName("Should request OTP for password reset successfully")
    void should_requestOtpForPasswordReset_when_userNotInEmailVerifyingState() {
        // Arrange
        String email = "test@example.com";
        User user = createTestUser(1, email, "Test User", UserStatus.ACTIVE, true, true);
        GetOtpRequest request = createGetOtpRequest(email, "FORGOT_PASSWORD");

        when(userRepository.findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.of(user));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(OtpUtil::generateOtp).thenReturn("789012");
            otpUtilMock.when(() -> OtpUtil.storeOtp(anyString(), anyString(), any(OtpFor.class))).thenAnswer(invocation -> null);

            // Act
            OtpResponse result = otpService.requestOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("OTP sent successfully");
            assertThat(result.getOtpFor()).isEqualTo("FORGOT_PASSWORD");
            assertThat(result.getVerifiedField()).isNull();

            verify(userRepository).findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING);
            verify(emailService).sendEmail(eq(email), eq("Reset your password"), 
                    eq("emails/otp-password-reset"), anyMap(), eq(EmailPriority.HIGH), eq(1L), eq("password-reset"));
            otpUtilMock.verify(() -> OtpUtil.generateOtp());
            otpUtilMock.verify(() -> OtpUtil.storeOtp(eq(email + ":FORGOT_PASSWORD"), eq("789012"), eq(OtpFor.FORGOT_PASSWORD)));
        }
    }

    @Test
    @DisplayName("Should throw exception when user not found for email verification")
    void should_throwException_when_userNotFoundForEmailVerification() {
        // Arrange
        String email = "test@example.com";
        GetOtpRequest request = createGetOtpRequest(email, "VERIFY_EMAIL");

        when(userRepository.findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> otpService.requestOtp(request))
                .isInstanceOf(BaseDomainException.class)
                .hasMessageContaining("User with email not in verifying state");

        verify(userRepository).findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING);
        verifyNoMoreInteractions(userRepository, emailService);
    }

    @Test
    @DisplayName("Should throw exception when user not found for phone verification")
    void should_throwException_when_userNotFoundForPhoneVerification() {
        // Arrange
        String email = "test@example.com";
        GetOtpRequest request = createGetOtpRequest(email, "VERIFY_PHONE");

        when(userRepository.findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> otpService.requestOtp(request))
                .isInstanceOf(BaseDomainException.class)
                .hasMessageContaining("An error occurred");

        verify(userRepository).findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING);
        verifyNoMoreInteractions(userRepository, emailService);
    }

    @ParameterizedTest
    @MethodSource("otpForProvider")
    @DisplayName("Should handle different OTP purposes correctly")
    void should_handleDifferentOtpPurposes_when_requestingOtp(OtpFor otpFor, String expectedSubject, String expectedTemplate, String expectedEmailType) {
        // Arrange
        String email = "test@example.com";
        User user = createTestUser(1, email, "Test User", 
                otpFor == OtpFor.VERIFY_EMAIL ? UserStatus.EMAIL_VERIFYING : UserStatus.PENDING, 
                false, false);
        GetOtpRequest request = createGetOtpRequest(email, otpFor.name());

        if (otpFor == OtpFor.VERIFY_EMAIL) {
            when(userRepository.findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING))
                    .thenReturn(Optional.of(user));
        } else {
            when(userRepository.findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING))
                    .thenReturn(Optional.of(user));
        }

        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(OtpUtil::generateOtp).thenReturn("123456");
            otpUtilMock.when(() -> OtpUtil.storeOtp(anyString(), anyString(), any(OtpFor.class))).thenAnswer(invocation -> null);

            // Act
            OtpResponse result = otpService.requestOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("OTP sent successfully");
            assertThat(result.getOtpFor()).isEqualTo(otpFor.name());

            verify(emailService).sendEmail(eq(email), eq(expectedSubject), 
                    eq(expectedTemplate), anyMap(), eq(EmailPriority.HIGH), eq(1L), eq(expectedEmailType));
        }
    }

    // Tests for verifyOtp method
    @Test
    @DisplayName("Should verify OTP for email verification successfully")
    void should_verifyOtpForEmailVerification_when_validOtpProvided() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "123456";
        User user = createTestUser(1, email, "Test User", UserStatus.EMAIL_VERIFYING, false, false);
        OtpRequest request = createOtpRequest(email, "VERIFY_EMAIL", otpCode);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(riderProfileRepository.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());
        when(riderProfileRepository.save(any(RiderProfile.class))).thenReturn(createTestRiderProfile(user));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(() -> OtpUtil.validateOtp(anyString(), anyString(), any(OtpFor.class))).thenReturn(true);

            // Act
            OtpResponse result = otpService.verifyOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("Email verified successfully");
            assertThat(result.getOtpFor()).isEqualTo("VERIFY_EMAIL");
            assertThat(result.getVerifiedField()).isEqualTo("email");

            verify(userRepository).findByEmail(email);
            verify(userRepository).existsByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING);
            verify(userRepository).save(any(User.class));
            otpUtilMock.verify(() -> OtpUtil.validateOtp(eq(email + ":VERIFY_EMAIL"), eq(otpCode), eq(OtpFor.VERIFY_EMAIL)));
        }
    }

    @Test
    @DisplayName("Should verify OTP for phone verification successfully")
    void should_verifyOtpForPhoneVerification_when_validOtpProvided() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "654321";
        User user = createTestUser(1, email, "Test User", UserStatus.PENDING, true, false);
        OtpRequest request = createOtpRequest(email, "VERIFY_PHONE", otpCode);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(riderProfileRepository.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());
        when(riderProfileRepository.save(any(RiderProfile.class))).thenReturn(createTestRiderProfile(user));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(() -> OtpUtil.validateOtp(anyString(), anyString(), any(OtpFor.class))).thenReturn(true);

            // Act
            OtpResponse result = otpService.verifyOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("Phone verified successfully");
            assertThat(result.getOtpFor()).isEqualTo("VERIFY_PHONE");
            assertThat(result.getVerifiedField()).isEqualTo("phone");

            verify(userRepository).findByEmail(email);
            verify(userRepository).save(any(User.class));
            otpUtilMock.verify(() -> OtpUtil.validateOtp(eq(email + ":VERIFY_PHONE"), eq(otpCode), eq(OtpFor.VERIFY_PHONE)));
        }
    }

    @Test
    @DisplayName("Should verify OTP for password reset successfully")
    void should_verifyOtpForPasswordReset_when_validOtpProvided() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "789012";
        User user = createTestUser(1, email, "Test User", UserStatus.ACTIVE, true, true);
        OtpRequest request = createOtpRequest(email, "FORGOT_PASSWORD", otpCode);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(() -> OtpUtil.validateOtp(anyString(), anyString(), any(OtpFor.class))).thenReturn(true);

            // Act
            OtpResponse result = otpService.verifyOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("OTP verified successfully");
            assertThat(result.getOtpFor()).isEqualTo("FORGOT_PASSWORD");
            assertThat(result.getVerifiedField()).isNull();

            verify(userRepository).findByEmail(email);
            otpUtilMock.verify(() -> OtpUtil.validateOtp(eq(email + ":FORGOT_PASSWORD"), eq(otpCode), eq(OtpFor.FORGOT_PASSWORD)));
        }
    }

    @Test
    @DisplayName("Should throw exception when OTP is invalid")
    void should_throwException_when_otpIsInvalid() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "000000";
        OtpRequest request = createOtpRequest(email, "VERIFY_EMAIL", otpCode);

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(() -> OtpUtil.validateOtp(anyString(), anyString(), any(OtpFor.class))).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> otpService.verifyOtp(request))
                    .isInstanceOf(BaseDomainException.class)
                    .hasMessageContaining("Invalid or expired OTP");

            otpUtilMock.verify(() -> OtpUtil.validateOtp(eq(email + ":VERIFY_EMAIL"), eq(otpCode), eq(OtpFor.VERIFY_EMAIL)));
            verifyNoMoreInteractions(userRepository);
        }
    }

    @Test
    @DisplayName("Should throw exception when user not found during verification")
    void should_throwException_when_userNotFoundDuringVerification() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "123456";
        OtpRequest request = createOtpRequest(email, "VERIFY_EMAIL", otpCode);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(() -> OtpUtil.validateOtp(anyString(), anyString(), any(OtpFor.class))).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> otpService.verifyOtp(request))
                    .isInstanceOf(BaseDomainException.class)
                    .hasMessageContaining("User with email not found");

            verify(userRepository).findByEmail(email);
            otpUtilMock.verify(() -> OtpUtil.validateOtp(eq(email + ":VERIFY_EMAIL"), eq(otpCode), eq(OtpFor.VERIFY_EMAIL)));
        }
    }

    @Test
    @DisplayName("Should create rider profile when both email and phone are verified")
    void should_createRiderProfile_when_bothEmailAndPhoneVerified() {
        // Arrange
        String email = "test@example.com";
        String otpCode = "123456";
        User user = createTestUser(1, email, "Test User", UserStatus.EMAIL_VERIFYING, false, true);
        OtpRequest request = createOtpRequest(email, "VERIFY_EMAIL", otpCode);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(riderProfileRepository.findByUserUserId(user.getUserId())).thenReturn(Optional.empty());
        when(riderProfileRepository.save(any(RiderProfile.class))).thenReturn(createTestRiderProfile(user));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(() -> OtpUtil.validateOtp(anyString(), anyString(), any(OtpFor.class))).thenReturn(true);

            // Act
            OtpResponse result = otpService.verifyOtp(request);

            // Assert
            assertThat(result.getMessage()).isEqualTo("Email verified successfully");
            assertThat(result.getVerifiedField()).isEqualTo("email");

            verify(userRepository).save(any(User.class));
            verify(riderProfileRepository).findByUserUserId(user.getUserId());
            verify(riderProfileRepository).save(any(RiderProfile.class));
        }
    }

    @Test
    @DisplayName("Should handle email service exception gracefully")
    void should_handleEmailServiceException_when_emailServiceThrowsException() {
        // Arrange
        String email = "test@example.com";
        User user = createTestUser(1, email, "Test User", UserStatus.EMAIL_VERIFYING, false, false);
        GetOtpRequest request = createGetOtpRequest(email, "VERIFY_EMAIL");

        when(userRepository.findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.of(user));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString()))
                .thenThrow(new RuntimeException("Email service error"));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(OtpUtil::generateOtp).thenReturn("123456");
            otpUtilMock.when(() -> OtpUtil.storeOtp(anyString(), anyString(), any(OtpFor.class))).thenAnswer(invocation -> null);

            // Act & Assert
            assertThatThrownBy(() -> otpService.requestOtp(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Email service error");

            verify(userRepository).findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING);
            verify(emailService).sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString());
        }
    }

    @Test
    @DisplayName("Should handle template variables correctly")
    void should_handleTemplateVariables_when_sendingEmail() {
        // Arrange
        String email = "test@example.com";
        User user = createTestUser(1, email, "John Doe", UserStatus.EMAIL_VERIFYING, false, false);
        GetOtpRequest request = createGetOtpRequest(email, "VERIFY_EMAIL");

        when(userRepository.findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING))
                .thenReturn(Optional.of(user));
        when(emailService.sendEmail(anyString(), anyString(), anyString(), anyMap(), any(EmailPriority.class), anyLong(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MockedStatic<OtpUtil> otpUtilMock = mockStatic(OtpUtil.class)) {
            otpUtilMock.when(OtpUtil::generateOtp).thenReturn("123456");
            otpUtilMock.when(() -> OtpUtil.storeOtp(anyString(), anyString(), any(OtpFor.class))).thenAnswer(invocation -> null);

            // Act
            otpService.requestOtp(request);

            // Assert
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> templateVarsCaptor = ArgumentCaptor.forClass(Map.class);
            verify(emailService).sendEmail(eq(email), eq("Verify your email"), 
                    eq("emails/otp-email-verification"), templateVarsCaptor.capture(), 
                    eq(EmailPriority.HIGH), eq(1L), eq("email-verification"));

            Map<String, Object> templateVars = templateVarsCaptor.getValue();
            assertThat(templateVars.get("fullName")).isEqualTo("John Doe");
            assertThat(templateVars.get("otpCode")).isEqualTo("123456");
        }
    }

    // Parameter providers
    private static Stream<Arguments> otpForProvider() {
        return Stream.of(
                Arguments.of(OtpFor.VERIFY_EMAIL, "Verify your email", "emails/otp-email-verification", "email-verification"),
                Arguments.of(OtpFor.VERIFY_PHONE, "Verify your phone", "emails/otp-phone-verification", "phone-verification"),
                Arguments.of(OtpFor.FORGOT_PASSWORD, "Reset your password", "emails/otp-password-reset", "password-reset")
        );
    }
}
