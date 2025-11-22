package com.mssus.app.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for AI-identified popular routes.
 * Helps riders discover popular campus routes.
 *
 * @since 1.0.0 (AI Integration)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPopularRoutesResponse {

    /**
     * List of popular routes near the rider's location.
     */
    private List<PopularRouteInfo> popularRoutes;

    /**
     * Suggested destinations based on rider's location.
     */
    private List<DestinationSuggestion> suggestedDestinations;

    /**
     * Peak times for rides from this location.
     */
    private List<String> peakTimes;

    /**
     * General AI recommendations for the rider.
     */
    private String recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopularRouteInfo {
        private String routeName;
        private int rideCount;
        private String description;
        private String bestTimes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationSuggestion {
        private String destinationName;
        private Double latitude;
        private Double longitude;
        private String reason;
        private int popularityScore;
    }
}

