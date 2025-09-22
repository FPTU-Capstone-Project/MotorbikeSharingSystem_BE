package com.mssus.app.service.notification;

import com.mssus.app.BaseEvent.SmsSentEvent;
import com.mssus.app.config.properties.SmsConfigurationProperties;
import com.mssus.app.dto.request.SmsRequest;
import com.mssus.app.dto.response.notification.SmsProvider;
import com.mssus.app.dto.response.notification.SmsResult;
import com.mssus.app.exception.SmsException;
import com.mssus.app.service.SmsService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SmsServiceImpl implements SmsService {

    private final List<SmsProvider> smsProviders;
    private final SmsConfigurationProperties config;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;

    public SmsServiceImpl(
            List<SmsProvider> smsProviders,
            SmsConfigurationProperties config,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            RedisTemplate<String, Object> redisTemplate) {
        this.smsProviders = smsProviders.stream()
                .sorted(Comparator.comparing(SmsProvider::getPriority))
                .collect(Collectors.toList());
        this.config = config;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Async("smsExecutor")
    @Retryable(value = {SmsException.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public CompletableFuture<SmsResult> sendVerificationSms(String phoneNumber, Long userId) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (!isValidPhoneNumber(normalizedPhone)) {
                return CompletableFuture.completedFuture(
                    SmsResult.failure("Invalid phone number format", null));
            }

            if (!checkSmsRateLimit(normalizedPhone, "verification")) {
                return CompletableFuture.completedFuture(
                    SmsResult.failure("Rate limit exceeded", null));
            }

            String verificationCode = generateVerificationCode();
            storeVerificationCode(userId, normalizedPhone, verificationCode);

            SmsRequest request = SmsRequest.verification(normalizedPhone, userId, verificationCode);
            SmsResult result = sendSmsWithFallback(request);

            recordSmsMetrics(result, "verification");
            eventPublisher.publishEvent(new SmsSentEvent(userId, normalizedPhone, "verification", result.success()));

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Failed to send verification SMS to: {}", phoneNumber, e);
            SmsResult errorResult = SmsResult.failure("Internal error: " + e.getMessage(), null);
            recordSmsMetrics(errorResult, "verification");
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    @Override
    @Async("smsExecutor")
    public CompletableFuture<SmsResult> sendPasswordResetSms(String phoneNumber, String resetCode) {
        try {
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            if (!isValidPhoneNumber(normalizedPhone)) {
                return CompletableFuture.completedFuture(
                    SmsResult.failure("Invalid phone number format", null));
            }

            SmsRequest request = SmsRequest.passwordReset(normalizedPhone, resetCode);
            SmsResult result = sendSmsWithFallback(request);

            recordSmsMetrics(result, "password-reset");

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Failed to send password reset SMS to: {}", phoneNumber, e);
            SmsResult errorResult = SmsResult.failure("Internal error: " + e.getMessage(), null);
            recordSmsMetrics(errorResult, "password-reset");
            return CompletableFuture.completedFuture(errorResult);
        }
    }

    @Override
    public SmsResult sendSmsSync(SmsRequest request) {
        return sendSmsWithFallback(request);
    }

    private SmsResult sendSmsWithFallback(SmsRequest request) {
        for (SmsProvider provider : smsProviders) {
            try {
                log.debug("Attempting to send SMS via provider: {}", provider.getName());
                SmsResult result = provider.sendSms(request);

                if (result.success()) {
                    log.info("SMS sent successfully via {}: messageId={}",
                            provider.getName(), result.messageId());
                    return result;
                }

                log.warn("SMS send failed via {}: {}", provider.getName(), result.errorMessage());

            } catch (Exception e) {
                log.error("SMS provider {} failed with exception", provider.getName(), e);
            }
        }

        return SmsResult.failure("All SMS providers failed", null);
    }

    private boolean checkSmsRateLimit(String phoneNumber, String type) {
        String key = "sms_rate_limit:" + type + ":" + phoneNumber;
        String countStr = (String) redisTemplate.opsForValue().get(key);

        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        int maxAttempts = config.getRateLimit().getMaxAttemptsPerHour();

        if (count >= maxAttempts) {
            log.warn("SMS rate limit exceeded for phone: {} type: {}", phoneNumber, type);
            return false;
        }

        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(1));

        return true;
    }

    private String generateVerificationCode() {
        return String.format("%06d", new SecureRandom().nextInt(1000000));
    }

    private void storeVerificationCode(Long userId, String phoneNumber, String code) {
        String key = "sms_verification_code:" + userId + ":" + phoneNumber;
        redisTemplate.opsForValue().set(key, code, config.getVerificationCodeTtl());
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^\\d]", "");
        if (!digits.startsWith("84")) {
            digits = "84" + digits.substring(1);
        }
        return "+" + digits;
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber.matches("^\\+84[0-9]{9,10}$");
    }

    private void recordSmsMetrics(SmsResult result, String type) {
        meterRegistry.counter("sms.sent",
                "type", type,
                "status", result.success() ? "success" : "failure",
                "provider", result.provider() != null ? result.provider().getName() : "unknown"
        ).increment();

        if (result.success() && result.cost() != null) {
            meterRegistry.summary("sms.cost",
                    "type", type,
                    "provider", result.provider().getName()
            ).record(result.cost().doubleValue());
        }
    }
}
