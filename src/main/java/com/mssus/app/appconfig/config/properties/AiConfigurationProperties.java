package com.mssus.app.appconfig.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AI integration.
 * 
 * <p>
 * Properties are loaded from application.properties under 'app.ai' prefix.
 * Supports multiple AI providers (xAI, OpenAI) with graceful fallback.
 * </p>
 * 
 * @since 1.0.0 (AI Integration)
 */
@ConfigurationProperties(prefix = "app.ai")
@Data
public class AiConfigurationProperties {

    /**
     * Enable/disable AI features globally.
     * When false, system falls back to rule-based algorithm.
     * Default: false
     */
    private boolean enabled = false;

    /**
     * AI provider identifier.
     * Supported: "xai" (Grok), "openai" (GPT)
     * Default: "xai"
     */
    private String provider = "xai";

    /**
     * AI API endpoint URL.
     * xAI: https://api.x.ai/v1/chat/completions
     * OpenAI: https://api.openai.com/v1/chat/completions
     */
    private String apiUrl;

    /**
     * AI model to use.
     * xAI: "grok-beta"
     * OpenAI: "gpt-4o-mini"
     */
    private String model;

    /**
     * API key for authentication.
     * Should be provided via environment variable.
     */
    private String apiKey;

    /**
     * Request timeout in seconds.
     * Default: 5 seconds
     */
    private int timeoutSeconds = 5;

    /**
     * Maximum retry attempts on API failure.
     * Default: 2
     */
    private int maxRetries = 2;

    /**
     * Whether to fallback to base algorithm on AI failure.
     * Default: true (recommended for production)
     */
    private boolean fallbackToBaseAlgorithm = true;

    /**
     * Temperature setting for AI responses (0.0-1.0).
     * Lower = more deterministic, Higher = more creative
     * Default: 0.3
     */
    private double temperature = 0.3;

    /**
     * Maximum tokens in AI response.
     * Default: 150 (sufficient for ranking decisions)
     */
    private int maxTokens = 150;

    /**
     * Cache AI responses for similar requests (in minutes).
     * Default: 5 minutes
     */
    private int cacheDurationMinutes = 5;

    /**
     * Maximum number of candidates to send to AI for ranking.
     * Reduces API costs and response time.
     * Default: 10
     */
    private int maxCandidatesForAi = 10;

    /**
     * Pattern analysis configuration.
     */
    private PatternAnalysis patternAnalysis = new PatternAnalysis();

    /**
     * Pattern analysis settings.
     */
    @Data
    public static class PatternAnalysis {
        /**
         * Enable/disable pattern analysis scheduled job.
         * Default: true
         */
        private boolean enabled = true;

        /**
         * Cron expression for pattern analysis job.
         * Default: "0 0 3 * * *" (3 AM daily)
         */
        private String schedule = "0 0 3 * * *";

        /**
         * Number of days of historical data to analyze.
         * Default: 30 days
         */
        private int historicalDays = 30;

        /**
         * Minimum rides required for pattern analysis.
         * Default: 10
         */
        private int minRidesForAnalysis = 10;

        /**
         * Cache duration for popular routes (in hours).
         * Default: 24 hours
         */
        private int cacheHours = 24;
    }

    /**
     * Check if AI is properly configured and enabled.
     */
    public boolean isConfigured() {
        return enabled 
            && apiUrl != null && !apiUrl.isEmpty()
            && apiKey != null && !apiKey.isEmpty()
            && model != null && !model.isEmpty();
    }
}


