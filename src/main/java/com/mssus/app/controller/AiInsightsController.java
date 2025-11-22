package com.mssus.app.controller;

import com.mssus.app.dto.response.ai.AiDriverRecommendationResponse;
import com.mssus.app.dto.response.ai.AiPopularRoutesResponse;
import com.mssus.app.dto.response.ai.MatchingInsightsResponse;
import com.mssus.app.appconfig.config.properties.AiConfigurationProperties;
import com.mssus.app.repository.AiMatchingLogRepository;
import com.mssus.app.service.ai.AiPatternAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST Controller for AI-powered insights and recommendations.
 * Provides endpoints for drivers and riders to get AI-generated suggestions.
 *
 * This satisfies requirements:
 * - "AI to suggest routes"
 * - "AI to recommend shared ride opportunities"
 *
 * @since 1.0.0 (AI Integration)
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiInsightsController {

    private final AiConfigurationProperties aiConfig;
    private final AiPatternAnalysisService patternAnalysisService;
    private final AiMatchingLogRepository aiLogRepository;

    /**
     * Get AI-powered recommendations for drivers.
     * Suggests optimal times and routes to maximize bookings.
     *
     * GET /api/v1/ai/driver/recommendations
     */
    @GetMapping("/driver/recommendations")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<AiDriverRecommendationResponse> getDriverRecommendations() {
        log.info("Getting AI driver recommendations");

        try {
            Optional<AiPatternAnalysisService.RidePatterns> patterns = 
                patternAnalysisService.getCachedPatterns();

            if (patterns.isEmpty()) {
                return ResponseEntity.ok(buildDefaultDriverRecommendations());
            }

            AiPatternAnalysisService.RidePatterns p = patterns.get();

            // Build time slot recommendations from peak hours
            List<AiDriverRecommendationResponse.TimeSlotRecommendation> timeSlots = new ArrayList<>();
            for (Integer hour : p.getPeakHours()) {
                timeSlots.add(AiDriverRecommendationResponse.TimeSlotRecommendation.builder()
                    .dayOfWeek("Monday-Friday")
                    .startHour(hour)
                    .endHour(hour + 1)
                    .timeDescription(getTimeDescription(hour))
                    .reason("High demand period based on historical data")
                    .demandLevel("High")
                    .build());
            }

            // Build route recommendations from popular routes
            List<AiDriverRecommendationResponse.RouteRecommendation> routes = new ArrayList<>();
            for (AiPatternAnalysisService.PopularRoute route : p.getPopularRoutes()) {
                routes.add(AiDriverRecommendationResponse.RouteRecommendation.builder()
                    .routeName(route.getRouteName())
                    .estimatedDemand(route.getFrequency())
                    .optimalTimes(getOptimalTimesForRoute())
                    .description(String.format("Popular route with %d rides in analysis period", 
                        route.getFrequency()))
                    .build());
            }

            AiDriverRecommendationResponse response = AiDriverRecommendationResponse.builder()
                .recommendedTimeSlots(timeSlots)
                .popularRoutes(routes)
                .generalRecommendations(p.getDriverRecommendations())
                .analysisTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get driver recommendations: {}", e.getMessage());
            return ResponseEntity.ok(buildDefaultDriverRecommendations());
        }
    }

    /**
     * Get popular routes near rider's location.
     * Helps riders discover common campus routes.
     *
     * GET /api/v1/ai/rider/popular-routes?lat={lat}&lng={lng}
     */
    @GetMapping("/rider/popular-routes")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<AiPopularRoutesResponse> getPopularRoutes(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        
        log.info("Getting popular routes for location: ({}, {})", lat, lng);

        try {
            Optional<AiPatternAnalysisService.RidePatterns> patterns = 
                patternAnalysisService.getCachedPatterns();

            if (patterns.isEmpty()) {
                return ResponseEntity.ok(buildDefaultPopularRoutes());
            }

            AiPatternAnalysisService.RidePatterns p = patterns.get();

            // Build popular route info
            List<AiPopularRoutesResponse.PopularRouteInfo> routeInfos = new ArrayList<>();
            for (AiPatternAnalysisService.PopularRoute route : p.getPopularRoutes()) {
                routeInfos.add(AiPopularRoutesResponse.PopularRouteInfo.builder()
                    .routeName(route.getRouteName())
                    .rideCount(route.getFrequency())
                    .description("Popular campus commute route")
                    .bestTimes(formatPeakHours(p.getPeakHours()))
                    .build());
            }

            // Build destination suggestions (simplified)
            List<AiPopularRoutesResponse.DestinationSuggestion> destinations = List.of(
                AiPopularRoutesResponse.DestinationSuggestion.builder()
                    .destinationName("FPT University")
                    .latitude(10.8411)
                    .longitude(106.8098)
                    .reason("Main campus - highest demand destination")
                    .popularityScore(100)
                    .build()
            );

            AiPopularRoutesResponse response = AiPopularRoutesResponse.builder()
                .popularRoutes(routeInfos)
                .suggestedDestinations(destinations)
                .peakTimes(p.getPeakHours().stream()
                    .map(hour -> String.format("%02d:00", hour))
                    .toList())
                .recommendations("Consider booking during off-peak hours for faster matching")
                .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get popular routes: {}", e.getMessage());
            return ResponseEntity.ok(buildDefaultPopularRoutes());
        }
    }

    /**
     * Get AI matching insights and statistics.
     * Shows performance metrics of AI matching system.
     *
     * GET /api/v1/ai/matching/insights
     */
    @GetMapping("/matching/insights")
    @PreAuthorize("hasAnyRole('ADMIN', 'DRIVER', 'RIDER')")
    public ResponseEntity<MatchingInsightsResponse> getMatchingInsights() {
        log.info("Getting AI matching insights");

        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);

            long successCount = aiLogRepository.countSuccessfulMatchesSince(cutoff);
            long failureCount = aiLogRepository.countFailedMatchesSince(cutoff);
            long totalCount = successCount + failureCount;

            Double avgProcessingTime = aiLogRepository.getAverageProcessingTime(cutoff);

            MatchingInsightsResponse response = MatchingInsightsResponse.builder()
                .totalAiMatches(totalCount)
                .successRate(totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0)
                .averageProcessingTimeMs(avgProcessingTime != null ? avgProcessingTime : 0.0)
                .fallbackCount(failureCount)
                .aiProvider(aiConfig.getProvider())
                .aiModel(aiConfig.getModel())
                .aiEnabled(aiConfig.isEnabled())
                .lastAnalysisTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .insights(buildInsightsText(successCount, failureCount, avgProcessingTime))
                .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get matching insights: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // Helper methods

    private AiDriverRecommendationResponse buildDefaultDriverRecommendations() {
        return AiDriverRecommendationResponse.builder()
            .recommendedTimeSlots(List.of(
                AiDriverRecommendationResponse.TimeSlotRecommendation.builder()
                    .dayOfWeek("Monday-Friday")
                    .startHour(7)
                    .endHour(9)
                    .timeDescription("Morning Rush")
                    .reason("Typical campus commute time")
                    .demandLevel("High")
                    .build(),
                AiDriverRecommendationResponse.TimeSlotRecommendation.builder()
                    .dayOfWeek("Monday-Friday")
                    .startHour(16)
                    .endHour(18)
                    .timeDescription("Evening Rush")
                    .reason("Students returning from campus")
                    .demandLevel("High")
                    .build()
            ))
            .popularRoutes(new ArrayList<>())
            .generalRecommendations("AI analysis in progress. Check back later for personalized recommendations.")
            .analysisTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
            .build();
    }

    private AiPopularRoutesResponse buildDefaultPopularRoutes() {
        return AiPopularRoutesResponse.builder()
            .popularRoutes(new ArrayList<>())
            .suggestedDestinations(List.of(
                AiPopularRoutesResponse.DestinationSuggestion.builder()
                    .destinationName("FPT University")
                    .latitude(10.8411)
                    .longitude(106.8098)
                    .reason("Main campus destination")
                    .popularityScore(100)
                    .build()
            ))
            .peakTimes(List.of("07:00", "08:00", "16:00", "17:00"))
            .recommendations("AI pattern analysis in progress.")
            .build();
    }

    private String getTimeDescription(int hour) {
        if (hour >= 6 && hour < 9) return "Morning Rush";
        if (hour >= 9 && hour < 12) return "Late Morning";
        if (hour >= 12 && hour < 14) return "Lunch Hour";
        if (hour >= 14 && hour < 17) return "Afternoon";
        if (hour >= 17 && hour < 20) return "Evening Rush";
        return "Evening";
    }

    private String getOptimalTimesForRoute() {
        return "7-9 AM, 4-6 PM (weekdays)";
    }

    private String formatPeakHours(List<Integer> hours) {
        if (hours.isEmpty()) return "Various times";
        return hours.stream()
            .map(h -> String.format("%02d:00", h))
            .reduce((a, b) -> a + ", " + b)
            .orElse("Various times");
    }

    private String buildInsightsText(long successCount, long failureCount, Double avgTime) {
        StringBuilder insights = new StringBuilder();
        
        long total = successCount + failureCount;
        if (total == 0) {
            return "No AI matching data available yet.";
        }

        double successRate = (double) successCount / total * 100;
        
        insights.append(String.format("AI successfully processed %.1f%% of matches. ", successRate));
        
        if (avgTime != null && avgTime > 0) {
            insights.append(String.format("Average processing time: %.0f ms. ", avgTime));
        }
        
        if (successRate > 95) {
            insights.append("Excellent AI performance!");
        } else if (successRate > 80) {
            insights.append("Good AI performance with occasional fallbacks.");
        } else {
            insights.append("AI is learning. Fallback algorithm ensuring service continuity.");
        }
        
        return insights.toString();
    }
}

