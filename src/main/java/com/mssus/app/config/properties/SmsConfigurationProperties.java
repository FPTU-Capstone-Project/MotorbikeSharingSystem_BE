package com.mssus.app.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.sms")
@Data
public class SmsConfigurationProperties {
    private Duration verificationCodeTtl = Duration.ofMinutes(10);
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        private int maxAttemptsPerHour = 3;
    }
}
