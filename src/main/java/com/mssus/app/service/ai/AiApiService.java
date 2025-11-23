package com.mssus.app.service.ai;

import com.mssus.app.appconfig.config.properties.AiConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Core AI API service for communicating with AI providers (xAI, OpenAI).
 * Handles API calls, retries, error handling, and response parsing.
 *
 * @since 1.0.0 (AI Integration)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiApiService {

    private final AiConfigurationProperties aiConfig;
    private final RestTemplate restTemplate = createRestTemplate();
    private static final String PROVIDER_GEMINI_NATIVE = "gemini-native";

    /**
     * Query the AI API with a prompt and get text response.
     *
     * @param systemPrompt System-level instructions for AI
     * @param userPrompt User query/context
     * @return AI response text
     * @throws AiServiceException if API call fails after retries
     */
    public String queryAi(String systemPrompt, String userPrompt) {
        if (!aiConfig.isConfigured()) {
            throw new AiServiceException("AI is not properly configured. Check API key and URL.");
        }

        log.debug("Querying AI with system prompt length: {}, user prompt length: {}", 
            systemPrompt.length(), userPrompt.length());

        int attempt = 0;
        Exception lastException = null;

        while (attempt <= aiConfig.getMaxRetries()) {
            try {
                if (attempt > 0) {
                    log.info("Retry attempt {} of {}", attempt, aiConfig.getMaxRetries());
                    // Exponential backoff: wait 1s, 2s, 4s...
                    TimeUnit.SECONDS.sleep((long) Math.pow(2, attempt - 1));
                }

                String response = callAiApi(systemPrompt, userPrompt);
                log.debug("AI response received: {}", response.substring(0, Math.min(100, response.length())));
                return response;

            } catch (HttpClientErrorException e) {
                // 4xx errors (bad request, unauthorized, etc.) - don't retry
                log.error("AI API client error ({}): {}", e.getStatusCode(), e.getMessage());
                throw new AiServiceException("AI API request error: " + e.getMessage(), e);

            } catch (NonRetryableAiServiceException e) {
                // Parsing/format issues won't be fixed by retrying
                log.error("AI API non-retriable error: {}", e.getMessage());
                throw e;

            } catch (AiServiceException e) {
                // Other service errors should not burn retries
                log.error("AI service error: {}", e.getMessage());
                throw e;

            } catch (HttpServerErrorException e) {
                // 5xx errors (server error) - retry
                lastException = e;
                log.warn("AI API server error ({}): {}", e.getStatusCode(), e.getMessage());
                attempt++;

            } catch (ResourceAccessException e) {
                // Timeout or connection error - retry
                lastException = e;
                log.warn("AI API connection error: {}", e.getMessage());
                attempt++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiServiceException("AI API call interrupted", e);

            } catch (Exception e) {
                lastException = e;
                log.error("Unexpected error calling AI API: {}", e.getMessage(), e);
                attempt++;
            }
        }

        // All retries exhausted
        throw new AiServiceException(
            String.format("AI API call failed after %d attempts", aiConfig.getMaxRetries()),
            lastException
        );
    }

    /**
     * Simplified query method with default system prompt.
     */
    public String queryAi(String userPrompt) {
        return queryAi(getDefaultSystemPrompt(), userPrompt);
    }

    /**
     * Make the actual HTTP call to AI API.
     */
    private String callAiApi(String systemPrompt, String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "MSSUS-RideSharing/1.0");

        // Gemini native uses API key header; OpenAI-style uses Bearer
        if (isGeminiNative()) {
            headers.set("x-goog-api-key", aiConfig.getApiKey());
        } else {
            headers.setBearerAuth(aiConfig.getApiKey());
        }

        Map<String, Object> requestBody = isGeminiNative()
            ? buildGeminiNativeRequest(systemPrompt, userPrompt)
            : buildOpenAiStyleRequest(systemPrompt, userPrompt);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        log.debug("Calling AI API: {} with model: {}", aiConfig.getApiUrl(), aiConfig.getModel());

        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                aiConfig.getApiUrl(),
                HttpMethod.POST,
                entity,
                Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new AiServiceException("AI API returned status: " + response.getStatusCode());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
            return extractResponseText(responseBody);

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error from AI API: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Extract text content from AI API response.
     * Handles both OpenAI and xAI response formats.
     */
    @SuppressWarnings("unchecked")
    private String extractResponseText(Map<String, Object> responseBody) {
        try {
            if (responseBody == null || responseBody.isEmpty()) {
                throw new NonRetryableAiServiceException("AI API response body is empty");
            }

            if (responseBody.containsKey("candidates")) {
                return extractGeminiNativeResponse(responseBody);
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new NonRetryableAiServiceException("AI API response has no choices");
            }

            Map<String, Object> firstChoice = choices.get(0);
            String finishReason = Objects.toString(firstChoice.get("finish_reason"), null);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            if (message == null) {
                throw new NonRetryableAiServiceException("AI API response has no message");
            }

            if (finishReason != null && !"stop".equalsIgnoreCase(finishReason)) {
                log.warn("AI finish_reason={} payload={}", finishReason, firstChoice);
            }

            Object contentObj = message.get("content");
            String content = extractContentText(contentObj);

            // Gemini can return refusals without content; surface and stop retrying
            Object refusal = message.get("refusal");
            if (refusal != null) {
                throw new NonRetryableAiServiceException(
                    String.format("AI refusal returned (finish_reason=%s): %s", finishReason, refusal));
            }

            if (finishReason != null && !"stop".equalsIgnoreCase(finishReason) && content == null) {
                throw new NonRetryableAiServiceException(
                    String.format("AI response not complete (finish_reason=%s) with empty content", finishReason));
            }

            if (content == null || content.trim().isEmpty()) {
                log.error("Empty AI content. First choice payload: {}", firstChoice);
                throw new NonRetryableAiServiceException("AI API response content is empty");
            }

            return content.trim();

        } catch (ClassCastException | NullPointerException e) {
            log.error("Failed to parse AI API response: {}", responseBody);
            throw new NonRetryableAiServiceException("Invalid AI API response format", e);
        }
    }

    /**
     * Extract text from native Gemini response shape (models/...:generateContent).
     */
    @SuppressWarnings("unchecked")
    private String extractGeminiNativeResponse(Map<String, Object> responseBody) {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new NonRetryableAiServiceException("AI API response has no candidates");
        }

        Map<String, Object> firstCandidate = candidates.get(0);
        String finishReason = Objects.toString(firstCandidate.get("finishReason"), null);
        Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");

        if (finishReason != null && !"stop".equalsIgnoreCase(finishReason)) {
            log.warn("Gemini finishReason={} payload={}", finishReason, firstCandidate);
        }

        if (content == null) {
            throw new NonRetryableAiServiceException("AI API response has no content");
        }

        String text = extractContentText(content);

        if (text == null || text.trim().isEmpty()) {
            log.error("Empty AI content (Gemini native). Candidate payload: {}", firstCandidate);
            throw new NonRetryableAiServiceException("AI API response content is empty");
        }

        return text.trim();
    }

    /**
     * Extract text content from Gemini/OpenAI style message content.
     * Handles both plain strings and list-of-part responses.
     */
    @SuppressWarnings("unchecked")
    private String extractContentText(Object contentObj) {
        if (contentObj == null) {
            return null;
        }

        if (contentObj instanceof Map<?, ?> map) {
            Object parts = map.get("parts");
            if (parts instanceof List<?> partList) {
                for (Object part : partList) {
                    String extracted = extractContentText(part);
                    if (extracted != null && !extracted.trim().isEmpty()) {
                        return extracted;
                    }
                }
            }
            Object text = map.get("text");
            if (text instanceof String s && !s.trim().isEmpty()) {
                return s;
            }
            Object nestedContent = map.get("content");
            if (nestedContent instanceof String s && !s.trim().isEmpty()) {
                return s;
            }
        }

        if (contentObj instanceof String str) {
            return str;
        }

        if (contentObj instanceof List<?> contentList) {
            for (Object part : contentList) {
                if (part instanceof String s && !s.trim().isEmpty()) {
                    return s;
                }
                if (part instanceof Map<?, ?> partMap) {
                    Object text = partMap.get("text");
                    if (text instanceof String s && !s.trim().isEmpty()) {
                        return s;
                    }
                    Object nestedContent = partMap.get("content");
                    if (nestedContent instanceof String s && !s.trim().isEmpty()) {
                        return s;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get default system prompt for ride matching.
     */
    private String getDefaultSystemPrompt() {
        return """
            You are an AI assistant for a university campus motorbike ride-sharing system.
            Your role is to analyze ride matching scenarios and provide recommendations.
            Always prioritize student safety and convenience.
            Provide concise, actionable responses.
            """;
    }

    /**
     * Create configured RestTemplate with timeout.
     */
    private RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        
        // Set timeout
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Accept", MediaType.APPLICATION_JSON_VALUE);
            return execution.execute(request, body);
        });
        
        return template;
    }

    private Map<String, Object> buildOpenAiStyleRequest(String systemPrompt, String userPrompt) {
        return Map.of(
            "model", aiConfig.getModel(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", aiConfig.getTemperature(),
            "max_tokens", aiConfig.getMaxTokens()
        );
    }

    private Map<String, Object> buildGeminiNativeRequest(String systemPrompt, String userPrompt) {
        return Map.of(
            "systemInstruction", Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
            ),
            "contents", List.of(
                Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", userPrompt))
                )
            ),
            "generationConfig", Map.of(
                "temperature", aiConfig.getTemperature(),
                "maxOutputTokens", aiConfig.getMaxTokens()
            )
        );
    }

    private boolean isGeminiNative() {
        String provider = aiConfig.getProvider() != null ? aiConfig.getProvider().toLowerCase() : "";
        String url = aiConfig.getApiUrl() != null ? aiConfig.getApiUrl().toLowerCase() : "";
        boolean urlHintsNative = url.contains(":generatecontent") || url.contains("/models/");
        return provider.contains(PROVIDER_GEMINI_NATIVE) || urlHintsNative;
    }

    /**
     * Check if AI service is available and configured.
     */
    public boolean isAvailable() {
        return aiConfig.isConfigured();
    }

    /**
     * Get AI provider name for logging/monitoring.
     */
    public String getProviderInfo() {
        return String.format("%s (%s)", aiConfig.getProvider(), aiConfig.getModel());
    }

    /**
     * Custom exception for AI service errors.
     */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) {
            super(message);
        }

        public AiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception type for errors that should not be retried (e.g., parsing/format issues).
     */
    public static class NonRetryableAiServiceException extends AiServiceException {
        public NonRetryableAiServiceException(String message) {
            super(message);
        }

        public NonRetryableAiServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

