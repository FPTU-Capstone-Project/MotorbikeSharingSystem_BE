package com.mssus.app.service.notification;

import com.mssus.app.BaseEvent.EmailSentEvent;
import com.mssus.app.config.properties.EmailConfigurationProperties;
import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.dto.response.notification.EmailProvider;
import com.mssus.app.dto.response.notification.EmailRequest;
import com.mssus.app.dto.response.notification.EmailResult;
import com.mssus.app.exception.EmailException;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.notification.EmailTemplateService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final List<EmailProvider> emailProviders;
    private final EmailTemplateService templateService;
    private final EmailConfigurationProperties config;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;

    public EmailServiceImpl(
            List<EmailProvider> emailProviders,
            EmailTemplateService templateService,
            EmailConfigurationProperties config,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            RedisTemplate<String, Object> redisTemplate) {
        this.emailProviders = emailProviders.stream()
                .sorted(Comparator.comparing(EmailProvider::getPriority))
                .collect(Collectors.toList());
        this.templateService = templateService;
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
    }
    @Override
    @Async("emailExecutor")
    @Retryable(value = {EmailException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<EmailResult> sendVerificationEmail(String email, Long userId) {
        try {
            if (!checkRateLimit(email, "verification")) {
                return CompletableFuture.completedFuture(
                    EmailResult.failure("Rate limit exceeded", null));
            }

            String verificationToken = generateVerificationToken(userId);
            storeVerificationToken(userId, verificationToken);

            Map<String, Object> templateVars = Map.of(
                "verificationToken", verificationToken,
                "verificationUrl", config.getFrontendBaseUrl() + "/verify-email?token=" + verificationToken,
                "userId", userId
            );

            EmailRequest request = new EmailRequest(
                email,
                "Verify your email address",
                "email-verification",
                templateVars,
                EmailPriority.HIGH,
                userId
            );

            EmailResult result = sendEmailWithFallback(request);
            recordEmailMetrics(result, "verification");
            eventPublisher.publishEvent(new EmailSentEvent(userId, email, "verification", result.success()));

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", email, e);
            EmailResult errorResult = EmailResult.failure("Internal error: " + e.getMessage(), null);
            recordEmailMetrics(errorResult, "verification");
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    @Override
    @Async("emailExecutor")
    public CompletableFuture<EmailResult> sendWelcomeEmail(String email, String fullName) {
        try {
            EmailRequest request = new EmailRequest(
                email,
                "Welcome to Our Platform",
                "welcome-email",
                Map.of("fullName", fullName),
                EmailPriority.NORMAL,
                null
            );

            EmailResult result = sendEmailWithFallback(request);
            recordEmailMetrics(result, "welcome");

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", email, e);
            EmailResult errorResult = EmailResult.failure("Internal error: " + e.getMessage(), null);
            recordEmailMetrics(errorResult, "welcome");
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    @Override
    @Async("emailExecutor")
    public CompletableFuture<EmailResult> sendPasswordResetEmail(String email, String resetToken) {
        try {
            EmailRequest request = new EmailRequest(
                email,
                "Password Reset Request",
                "password-reset",
                Map.of(
                    "resetToken", resetToken,
                    "resetUrl", buildPasswordResetUrl(resetToken)
                ),
                EmailPriority.HIGH,
                null
            );

            EmailResult result = sendEmailWithFallback(request);
            recordEmailMetrics(result, "password-reset");

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
            EmailResult errorResult = EmailResult.failure("Internal error: " + e.getMessage(), null);
            recordEmailMetrics(errorResult, "password-reset");
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    @Override
    public EmailResult sendEmailSync(EmailRequest request) {
        return sendEmailWithFallback(request);
    }

    // Helper Method

    private boolean checkRateLimit(String email, String type){
        String key = "email:rate_limit:" + type + ":" + email;
        String countStr = (String) redisTemplate.opsForValue().get(key);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        int maxAttempts = config.getRateLimit().getMaxAttemptsPerHour();

        if(count >= maxAttempts){
            log.warn("Rate limit exceeded for {} emails to {}", type, email);
            return false;
        }
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(1));
        return true;
    }
    private String generateVerificationToken(Long userId) {
        Map<String, String> claims = Map.of("type", "email_verification");
        byte[] keyBytes = Decoders.BASE64.decode(config.getJwtSecret());
        Key  signKey = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .setSubject(userId.toString())
                .setExpiration(Date.from(Instant.now().plus(config.getVerificationTokenTtl())))
                .setIssuedAt(new Date())
                .setClaims(claims)
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }
    private void storeVerificationToken(Long userId, String token) {
        String key = "email_verification_token:" + userId;
        redisTemplate.opsForValue().set(key, token, config.getVerificationTokenTtl());
    }

    private EmailResult sendEmailWithFallback(EmailRequest request) {
        for (EmailProvider provider : emailProviders) {
            try {
                log.debug("Attempting to send email via provider: {}", provider.getName());
                EmailResult result = provider.sendEmail(request);

                if (result.success()) {
                    log.info("Email sent successfully via {}: messageId={}",
                            provider.getName(), result.messageId());
                    return result;
                }

                log.warn("Email send failed via {}: {}", provider.getName(), result.errorMessage());

            } catch (Exception e) {
                log.error("Email provider {} failed with exception", provider.getName(), e);
            }
        }

        return EmailResult.failure("All email providers failed", null);
    }

    private void recordEmailMetrics(EmailResult result, String type) {
        meterRegistry.counter("email.sent",
                "type", type,
                "status", result.success() ? "success" : "failure",
                "provider", result.provider() != null ? result.provider().getName() : "unknown"
        ).increment();
    }

    private String buildPasswordResetUrl(String resetToken) {
        return config.getFrontendBaseUrl() + "/reset-password?token=" + resetToken;
    }
}
