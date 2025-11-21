package com.mssus.app.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for AI matching insights and statistics.
 * Provides metrics about AI matching performance.
 *
 * @since 1.0.0 (AI Integration)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingInsightsResponse {

    /**
     * Total number of AI-processed matches.
     */
    private long totalAiMatches;

    /**
     * Success rate of AI matching.
     */
    private double successRate;

    /**
     * Average processing time in milliseconds.
     */
    private double averageProcessingTimeMs;

    /**
     * Number of times AI fallback was used.
     */
    private long fallbackCount;

    /**
     * AI provider information.
     */
    private String aiProvider;

    /**
     * AI model being used.
     */
    private String aiModel;

    /**
     * Whether AI is currently enabled.
     */
    private boolean aiEnabled;

    /**
     * Last analysis timestamp.
     */
    private String lastAnalysisTime;

    /**
     * Additional insights or recommendations.
     */
    private String insights;
}


