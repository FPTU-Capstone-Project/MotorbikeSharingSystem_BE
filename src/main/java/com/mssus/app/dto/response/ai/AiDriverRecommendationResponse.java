package com.mssus.app.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for AI-powered driver recommendations.
 * Provides optimal timing and routing suggestions for drivers.
 *
 * @since 1.0.0 (AI Integration)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDriverRecommendationResponse {

    /**
     * List of recommended time slots to offer rides.
     */
    private List<TimeSlotRecommendation> recommendedTimeSlots;

    /**
     * List of popular routes to focus on.
     */
    private List<RouteRecommendation> popularRoutes;

    /**
     * General AI recommendations for the driver.
     */
    private String generalRecommendations;

    /**
     * When the analysis was performed.
     */
    private String analysisTimestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlotRecommendation {
        private String dayOfWeek;
        private int startHour;
        private int endHour;
        private String timeDescription;
        private String reason;
        private String demandLevel; // "High", "Medium", "Low"
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRecommendation {
        private String routeName;
        private int estimatedDemand;
        private String optimalTimes;
        private String description;
    }
}


