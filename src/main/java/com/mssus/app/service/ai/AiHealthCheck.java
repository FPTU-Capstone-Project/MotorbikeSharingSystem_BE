package com.mssus.app.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Validates AI provider configuration on application startup.
 * Sends a test prompt to ensure the API key and endpoint are working correctly.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiHealthCheck implements CommandLineRunner {

    private final AiApiService aiApiService;

    @Override
    public void run(String... args) {
        log.info("=== Performing AI Provider Health Check ===");

        try {
            String systemPrompt = "You are a helpful assistant.";
            String userPrompt = "If you are available, reply \"hello\"";

            log.info("Sending test prompt to AI provider...");
            String response = aiApiService.queryAi(systemPrompt, userPrompt);

            if (response != null && response.toLowerCase().contains("hello")) {
                System.out.println("hello");
                log.info("AI Provider Health Check PASSED. Response: {}", response);
            } else {
                log.warn("AI Provider responded but not with expected greeting. Response: {}", response);
            }

        } catch (Exception e) {
            System.out.println("AI provider configuration encounters problem");
            log.error("AI Provider Health Check FAILED", e);
            log.error("Detailed error: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("Root cause: {}", e.getCause().getMessage());
            }
        }

        log.info("===========================================");
    }
}
