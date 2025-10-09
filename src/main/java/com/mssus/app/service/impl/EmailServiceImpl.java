package com.mssus.app.service.impl;

import com.mssus.app.common.exception.EmailException;
import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.dto.response.notification.EmailRequest;
import com.mssus.app.dto.response.notification.EmailResult;
import com.mssus.app.dto.response.notification.EmailProviderType;
import com.mssus.app.entity.User;
import com.mssus.app.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.email.from-address}")
    private String fromAddress;

    @Value("${app.email.from-name}")
    private String fromName;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));



    @Override
    @Async
    public CompletableFuture<EmailResult> sendTopUpSuccessEmail(String email, String fullName, BigDecimal amount, String transactionId, BigDecimal newBalance) {
        try {
            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("amount", formatCurrency(amount));
            context.setVariable("newBalance", formatCurrency(newBalance));
            context.setVariable("transactionId", transactionId);
            context.setVariable("transactionTime", LocalDateTime.now().format(DATE_FORMATTER));
            context.setVariable("supportEmail", fromAddress);
            context.setVariable("frontendUrl", frontendBaseUrl);

            String htmlContent = templateEngine.process("emails/topup-success", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(email);
            helper.setSubject("[Motorbike Sharing] Nạp tiền thành công - " + transactionId);
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

            log.info("Top-up success email sent to: {} for transaction: {}", email, transactionId);
            return CompletableFuture.completedFuture(EmailResult.success("Top-up success email sent successfully"));

        } catch (Exception e) {
            log.error("Failed to send top-up success email to: {} for transaction: {}", email, transactionId, e);
            return CompletableFuture.completedFuture(EmailResult.failure("Failed to send top-up success email: " + e.getMessage()));
        }
    }

    @Override
    @Async
    public CompletableFuture<EmailResult> sendPaymentFailedEmail(String email, String fullName, BigDecimal amount, String transactionId, String reason) {
        try {
            Context context = new Context();
            context.setVariable("fullName", fullName);
            context.setVariable("amount", formatCurrency(amount));
            context.setVariable("transactionId", transactionId);
            context.setVariable("reason", reason);
            context.setVariable("transactionTime", LocalDateTime.now().format(DATE_FORMATTER));
            context.setVariable("supportEmail", fromAddress);
            context.setVariable("frontendUrl", frontendBaseUrl);

            String htmlContent = templateEngine.process("emails/payment-failed", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(email);
            helper.setSubject("[Motorbike Sharing] Giao dịch không thành công - " + transactionId);
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

            log.info("Payment failed email sent to: {} for transaction: {}", email, transactionId);
            return CompletableFuture.completedFuture(EmailResult.success("Payment failed email sent successfully"));

        } catch (Exception e) {
            log.error("Failed to send payment failed email to: {} for transaction: {}", email, transactionId, e);
            return CompletableFuture.completedFuture(EmailResult.failure("Failed to send payment failed email: " + e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<EmailResult> notifyDriverActivated(User user) {
        try{
            Context context = new Context();
            context.setVariable("fullName", user.getFullName());
            context.setVariable("supportEmail", fromAddress);
            context.setVariable("email",user.getEmail());
            context.setVariable("approvalDate", LocalDateTime.now().format(DATE_FORMATTER));

            String htmlContent = templateEngine.process("emails/driver-verification-approved", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("[Motorbike Sharing] Tài khoản tài xế của bạn đã được kích hoạt");
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

            return CompletableFuture.completedFuture(EmailResult.success("Driver activated email sent successfully"));
        }catch (Exception e){
            log.error("Failed to send driver activated email to: {}", user.getEmail(), e);
            return CompletableFuture.completedFuture(EmailResult.failure("Failed to send driver activated email: " + e.getMessage()));
        }
    }

    @Override
    public CompletableFuture<EmailResult> notifyDriverSuspended(User user) {
        try{
            Context context = new Context();
            context.setVariable("fullName", user.getFullName());
            context.setVariable("supportEmail", fromAddress);
            context.setVariable("email",user.getEmail());
            context.setVariable("rejectionDate", LocalDateTime.now().format(DATE_FORMATTER));

            String htmlContent = templateEngine.process("emails/driver-verification-rejected", context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("[Motorbike Sharing] Tài khoản tài xế của bạn đã bị khóa");
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

            return CompletableFuture.completedFuture(EmailResult.success("Driver suspended email sent successfully"));
        }catch (Exception e){
            log.error("Failed to send driver activated email to: {}", user.getEmail(), e);
            return CompletableFuture.completedFuture(EmailResult.failure("Failed to send driver activated email: " + e.getMessage()));
        }
    }

    @Override
    @Async
    @Retryable(value = {EmailException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<EmailResult> sendEmail(String email, String subject, String templateName,
                                                    Map<String, Object> templateVars, EmailPriority priority,
                                                    Long userId, String emailType) {
        try {
            Context context = new Context();
            if (templateVars != null) {
                templateVars.forEach(context::setVariable);
            }
            context.setVariable("supportEmail", fromAddress);
            context.setVariable("frontendUrl", frontendBaseUrl);

            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress, fromName);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            javaMailSender.send(message);

            log.info("Email sent to: {} with template: {}", email, templateName);
            return CompletableFuture.completedFuture(EmailResult.success("Email sent successfully"));

        } catch (Exception e) {
            log.error("Failed to send email to: {} with template: {}", email, templateName, e);
            return CompletableFuture.completedFuture(EmailResult.failure("Failed to send email: " + e.getMessage()));
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return String.format("%,.0f ₫", amount.doubleValue());
    }
}