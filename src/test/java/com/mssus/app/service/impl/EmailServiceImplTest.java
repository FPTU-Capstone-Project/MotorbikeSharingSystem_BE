package com.mssus.app.service.impl;

import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.dto.response.notification.EmailResult;
import com.mssus.app.entity.User;
import jakarta.mail.internet.MimeMessage;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl Tests")
class EmailServiceImplTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private MimeMessageHelper mimeMessageHelper;

    @InjectMocks
    private EmailServiceImpl emailService;

    private static final String FROM_ADDRESS = "noreply@motorbikesharing.com";
    private static final String FROM_NAME = "Motorbike Sharing System";
    private static final String FRONTEND_BASE_URL = "https://motorbikesharing.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", FROM_ADDRESS);
        ReflectionTestUtils.setField(emailService, "fromName", FROM_NAME);
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", FRONTEND_BASE_URL);
    }

    // Helper methods
    private User createTestUser() {
        return User.builder()
                .userId(1)
                .email("test@example.com")
                .phone("0123456789")
                .fullName("Test User")
                .studentId("ST001")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .gender("Male")
                .emailVerified(true)
                .phoneVerified(true)
                .build();
    }

    private User createTestUserWithNullFields() {
        return User.builder()
                .userId(2)
                .email("nulltest@example.com")
                .phone("0987654321")
                .fullName(null)
                .studentId(null)
                .dateOfBirth(null)
                .gender(null)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }

    private Map<String, Object> createTestTemplateVars() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", "Test User");
        vars.put("otpCode", "123456");
        return vars;
    }

    // Tests for sendTopUpSuccessEmail
    @Test
    @DisplayName("Should send top-up success email successfully when all parameters are valid")
    void should_sendTopUpSuccessEmail_when_allParametersValid() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = new BigDecimal("100000");
        String transactionId = "TXN123456";
        BigDecimal newBalance = new BigDecimal("500000");
        String expectedHtmlContent = "<html>Top-up success email</html>";

        when(templateEngine.process(eq("emails/topup-success"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendTopUpSuccessEmail(
                email, fullName, amount, transactionId, newBalance);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();
        assertThat(emailResult.messageId()).isEqualTo("Top-up success email sent successfully");

        verify(templateEngine).process(eq("emails/topup-success"), any(Context.class));
        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender).send(mimeMessage);
        verifyNoMoreInteractions(templateEngine, javaMailSender);
    }

    @Test
    @DisplayName("Should handle null amount in top-up success email")
    void should_handleNullAmount_when_sendingTopUpSuccessEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = null;
        String transactionId = "TXN123456";
        BigDecimal newBalance = new BigDecimal("500000");
        String expectedHtmlContent = "<html>Top-up success email</html>";

        when(templateEngine.process(eq("emails/topup-success"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendTopUpSuccessEmail(
                email, fullName, amount, transactionId, newBalance);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/topup-success"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("amount")).isEqualTo("0 ₫");
    }

    @Test
    @DisplayName("Should handle template processing failure in top-up success email")
    void should_returnFailureResult_when_templateProcessingFails() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = new BigDecimal("100000");
        String transactionId = "TXN123456";
        BigDecimal newBalance = new BigDecimal("500000");

        when(templateEngine.process(eq("emails/topup-success"), any(Context.class)))
                .thenThrow(new RuntimeException("Template processing failed"));

        // Act
        CompletableFuture<EmailResult> result = emailService.sendTopUpSuccessEmail(
                email, fullName, amount, transactionId, newBalance);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isFalse();
        assertThat(emailResult.getErrorMessage()).contains("Failed to send top-up success email");
        assertThat(emailResult.getErrorMessage()).contains("Template processing failed");
    }

    // Tests for sendPaymentFailedEmail
    @Test
    @DisplayName("Should send payment failed email successfully when all parameters are valid")
    void should_sendPaymentFailedEmail_when_allParametersValid() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = new BigDecimal("50000");
        String transactionId = "TXN789012";
        String reason = "Insufficient funds";
        String expectedHtmlContent = "<html>Payment failed email</html>";

        when(templateEngine.process(eq("emails/payment-failed"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendPaymentFailedEmail(
                email, fullName, amount, transactionId, reason);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();
        assertThat(emailResult.messageId()).isEqualTo("Payment failed email sent successfully");

        verify(templateEngine).process(eq("emails/payment-failed"), any(Context.class));
        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle null reason in payment failed email")
    void should_handleNullReason_when_sendingPaymentFailedEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = new BigDecimal("50000");
        String transactionId = "TXN789012";
        String reason = null;
        String expectedHtmlContent = "<html>Payment failed email</html>";

        when(templateEngine.process(eq("emails/payment-failed"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendPaymentFailedEmail(
                email, fullName, amount, transactionId, reason);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/payment-failed"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("reason")).isNull();
    }

    // Tests for notifyUserActivated
    @Test
    @DisplayName("Should send user activation notification successfully when user is valid")
    void should_sendUserActivationNotification_when_userIsValid() throws Exception {
        // Arrange
        User user = createTestUser();
        String expectedHtmlContent = "<html>User activated email</html>";

        when(templateEngine.process(eq("emails/driver-verification-approved"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.notifyUserActivated(user);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();
        assertThat(emailResult.messageId()).isEqualTo("Driver activated email sent successfully");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/driver-verification-approved"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("fullName")).isEqualTo(user.getFullName());
        assertThat(capturedContext.getVariable("email")).isEqualTo(user.getEmail());
        assertThat(capturedContext.getVariable("supportEmail")).isEqualTo(FROM_ADDRESS);
    }

    @Test
    @DisplayName("Should handle user with null fullName in activation notification")
    void should_handleUserWithNullFullName_when_sendingActivationNotification() throws Exception {
        // Arrange
        User user = createTestUserWithNullFields();
        String expectedHtmlContent = "<html>User activated email</html>";

        when(templateEngine.process(eq("emails/driver-verification-approved"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.notifyUserActivated(user);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/driver-verification-approved"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("fullName")).isNull();
    }

    // Tests for notifyUserRejected
    @Test
    @DisplayName("Should send user rejection notification successfully when all parameters are valid")
    void should_sendUserRejectionNotification_when_allParametersValid() throws Exception {
        // Arrange
        User user = createTestUser();
        VerificationType type = VerificationType.DRIVER_LICENSE;
        String reason = "Document quality is poor";
        String expectedHtmlContent = "<html>User rejected email</html>";

        when(templateEngine.process(eq("emails/driver-verification-rejected"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.notifyUserRejected(user, type, reason);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();
        assertThat(emailResult.messageId()).isEqualTo("User rejected email sent successfully");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/driver-verification-rejected"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("fullName")).isEqualTo(user.getFullName());
        assertThat(capturedContext.getVariable("rejectionReason")).isEqualTo(reason);
        assertThat(capturedContext.getVariable("rejectionType")).isEqualTo(type.toString());
    }

    @ParameterizedTest
    @MethodSource("verificationTypeProvider")
    @DisplayName("Should handle different verification types in rejection notification")
    void should_handleDifferentVerificationTypes_when_sendingRejectionNotification(VerificationType type) throws Exception {
        // Arrange
        User user = createTestUser();
        String reason = "Invalid document";
        String expectedHtmlContent = "<html>User rejected email</html>";

        when(templateEngine.process(eq("emails/driver-verification-rejected"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.notifyUserRejected(user, type, reason);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/driver-verification-rejected"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("rejectionType")).isEqualTo(type.toString());
    }

    // Tests for sendEmail
    @Test
    @DisplayName("Should send generic email successfully when all parameters are valid")
    void should_sendGenericEmail_when_allParametersValid() throws Exception {
        // Arrange
        String email = "test@example.com";
        String subject = "Test Subject";
        String templateName = "emails/test-template";
        Map<String, Object> templateVars = createTestTemplateVars();
        EmailPriority priority = EmailPriority.HIGH;
        Long userId = 1L;
        String emailType = "TEST";
        String expectedHtmlContent = "<html>Test email</html>";

        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendEmail(
                email, subject, templateName, templateVars, priority, userId, emailType);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();
        assertThat(emailResult.messageId()).isEqualTo("Email sent successfully");

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("fullName")).isEqualTo("Test User");
        assertThat(capturedContext.getVariable("otpCode")).isEqualTo("123456");
        assertThat(capturedContext.getVariable("supportEmail")).isEqualTo(FROM_ADDRESS);
        assertThat(capturedContext.getVariable("frontendUrl")).isEqualTo(FRONTEND_BASE_URL);
    }

    @Test
    @DisplayName("Should handle null template variables in generic email")
    void should_handleNullTemplateVariables_when_sendingGenericEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String subject = "Test Subject";
        String templateName = "emails/test-template";
        Map<String, Object> templateVars = null;
        EmailPriority priority = EmailPriority.LOW;
        Long userId = 1L;
        String emailType = "TEST";
        String expectedHtmlContent = "<html>Test email</html>";

        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendEmail(
                email, subject, templateName, templateVars, priority, userId, emailType);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("supportEmail")).isEqualTo(FROM_ADDRESS);
        assertThat(capturedContext.getVariable("frontendUrl")).isEqualTo(FRONTEND_BASE_URL);
    }

    @Test
    @DisplayName("Should handle empty template variables in generic email")
    void should_handleEmptyTemplateVariables_when_sendingGenericEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String subject = "Test Subject";
        String templateName = "emails/test-template";
        Map<String, Object> templateVars = new HashMap<>();
        EmailPriority priority = EmailPriority.NORMAL;
        Long userId = 1L;
        String emailType = "TEST";
        String expectedHtmlContent = "<html>Test email</html>";

        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendEmail(
                email, subject, templateName, templateVars, priority, userId, emailType);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(templateName), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("supportEmail")).isEqualTo(FROM_ADDRESS);
        assertThat(capturedContext.getVariable("frontendUrl")).isEqualTo(FRONTEND_BASE_URL);
    }

    // Tests for formatCurrency method
    @ParameterizedTest
    @MethodSource("currencyFormatProvider")
    @DisplayName("Should format currency correctly for different amounts")
    void should_formatCurrencyCorrectly_when_givenDifferentAmounts(BigDecimal input, String expected) {
        // Act
        String result = ReflectionTestUtils.invokeMethod(emailService, "formatCurrency", input);

        // Assert
        assertThat(result).isEqualTo(expected);
    }

    // Exception handling tests
    @Test
    @DisplayName("Should handle RuntimeException in sendTopUpSuccessEmail")
    void should_handleRuntimeException_when_sendingTopUpSuccessEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = new BigDecimal("100000");
        String transactionId = "TXN123456";
        BigDecimal newBalance = new BigDecimal("500000");

        when(templateEngine.process(eq("emails/topup-success"), any(Context.class)))
                .thenReturn("<html>Test</html>");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP error")).when(javaMailSender).send(any(MimeMessage.class));

        // Act
        CompletableFuture<EmailResult> result = emailService.sendTopUpSuccessEmail(
                email, fullName, amount, transactionId, newBalance);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isFalse();
        assertThat(emailResult.getErrorMessage()).contains("Failed to send top-up success email");
        assertThat(emailResult.getErrorMessage()).contains("SMTP error");
    }

    @Test
    @DisplayName("Should handle RuntimeException in sendEmail")
    void should_handleRuntimeException_when_sendingGenericEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String subject = "Test Subject";
        String templateName = "emails/test-template";
        Map<String, Object> templateVars = createTestTemplateVars();
        EmailPriority priority = EmailPriority.HIGH;
        Long userId = 1L;
        String emailType = "TEST";

        when(templateEngine.process(eq(templateName), any(Context.class)))
                .thenThrow(new RuntimeException("Template engine error"));

        // Act
        CompletableFuture<EmailResult> result = emailService.sendEmail(
                email, subject, templateName, templateVars, priority, userId, emailType);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isFalse();
        assertThat(emailResult.getErrorMessage()).contains("Failed to send email");
        assertThat(emailResult.getErrorMessage()).contains("Template engine error");
    }

    // Edge cases and boundary tests
    @Test
    @DisplayName("Should handle very large BigDecimal amounts")
    void should_handleVeryLargeAmounts_when_sendingTopUpSuccessEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = new BigDecimal("999999999999.99");
        String transactionId = "TXN123456";
        BigDecimal newBalance = new BigDecimal("999999999999.99");
        String expectedHtmlContent = "<html>Top-up success email</html>";

        when(templateEngine.process(eq("emails/topup-success"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendTopUpSuccessEmail(
                email, fullName, amount, transactionId, newBalance);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/topup-success"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("amount")).isEqualTo("1.000.000.000.000 ₫");
    }

    @Test
    @DisplayName("Should handle zero amount in payment failed email")
    void should_handleZeroAmount_when_sendingPaymentFailedEmail() throws Exception {
        // Arrange
        String email = "test@example.com";
        String fullName = "Test User";
        BigDecimal amount = BigDecimal.ZERO;
        String transactionId = "TXN789012";
        String reason = "Invalid amount";
        String expectedHtmlContent = "<html>Payment failed email</html>";

        when(templateEngine.process(eq("emails/payment-failed"), any(Context.class)))
                .thenReturn(expectedHtmlContent);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Act
        CompletableFuture<EmailResult> result = emailService.sendPaymentFailedEmail(
                email, fullName, amount, transactionId, reason);

        // Assert
        EmailResult emailResult = result.get();
        assertThat(emailResult.isSuccess()).isTrue();

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("emails/payment-failed"), contextCaptor.capture());
        
        Context capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.getVariable("amount")).isEqualTo("0 ₫");
    }

    // Parameter providers
    private static Stream<Arguments> verificationTypeProvider() {
        return Stream.of(
                Arguments.of(VerificationType.STUDENT_ID),
                Arguments.of(VerificationType.DRIVER_LICENSE),
                Arguments.of(VerificationType.BACKGROUND_CHECK),
                Arguments.of(VerificationType.VEHICLE_REGISTRATION),
                Arguments.of(VerificationType.DRIVER_DOCUMENTS)
        );
    }

    private static Stream<Arguments> currencyFormatProvider() {
        return Stream.of(
                Arguments.of(null, "0 ₫"),
                Arguments.of(BigDecimal.ZERO, "0 ₫"),
                Arguments.of(new BigDecimal("1000"), "1.000 ₫"),
                Arguments.of(new BigDecimal("1000000"), "1.000.000 ₫"),
                Arguments.of(new BigDecimal("1234567.89"), "1.234.568 ₫"),
                Arguments.of(new BigDecimal("999999999.99"), "1.000.000.000 ₫"),
                Arguments.of(new BigDecimal("-1000"), "-1.000 ₫")
        );
    }
}
