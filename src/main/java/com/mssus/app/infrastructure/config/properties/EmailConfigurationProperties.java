package com.mssus.app.infrastructure.config.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.email")
@Data
public class EmailConfigurationProperties {
    private Duration verificationTokenTtl = Duration.ofHours(24);
    private String jwtSecret;
    private RateLimit rateLimit = new RateLimit();
    private String fromAddress = "motorbikesharingcap@gmail.com";
    private String fromName = "Motorbike Sharing";

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Data
    public static class RateLimit {
        private int maxAttemptsPerHour = 5;
    }
}
