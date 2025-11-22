package com.mssus.app.service.ai;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.appconfig.config.properties.AiConfigurationProperties;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.service.ai.AiApiService.AiServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Pattern Analysis Service.
 * Analyzes historical ride data to identify patterns, popular routes, and peak times.
 * Runs as scheduled job to continuously learn from ride data.
 *
 * This satisfies the requirement: "AI shall analyze ride patterns to identify popular shared routes to campuses"
 *
 * @since 1.0.0 (AI Integration)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiPatternAnalysisService {

    private final AiApiService aiApiService;
    private final AiConfigurationProperties aiConfig;
    private final SharedRideRepository rideRepository;

    // In-memory cache for pattern analysis results
    private volatile RidePatterns cachedPatterns;
    private volatile LocalDateTime lastAnalysisTime;

    /**
     * Scheduled job to analyze ride patterns using AI.
     * Runs daily at configured time (default: 3 AM).
     */
    @Scheduled(cron = "${app.ai.pattern-analysis.schedule:0 0 3 * * *}")
    @ConditionalOnProperty(prefix = "app.ai.pattern-analysis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public void analyzeRidePatterns() {
        log.info("=== Starting AI Pattern Analysis ===");

        try {
            // Check if AI is available
            if (!aiApiService.isAvailable()) {
                log.warn("AI service not available, skipping pattern analysis");
                return;
            }

            // Fetch historical data
            LocalDateTime cutoffDate = LocalDateTime.now()
                .minusDays(aiConfig.getPatternAnalysis().getHistoricalDays());
            
            List<SharedRide> completedRides = rideRepository
                .findAll()
                .stream()
                .filter(ride -> ride.getStatus() == SharedRideStatus.COMPLETED)
                .filter(ride -> ride.getScheduledTime() != null && 
                               ride.getScheduledTime().isAfter(cutoffDate))
                .toList();

            if (completedRides.size() < aiConfig.getPatternAnalysis().getMinRidesForAnalysis()) {
                log.info("Insufficient ride data for pattern analysis. Found: {}, Required: {}",
                    completedRides.size(), aiConfig.getPatternAnalysis().getMinRidesForAnalysis());
                return;
            }

            log.info("Analyzing {} completed rides from the last {} days",
                completedRides.size(), aiConfig.getPatternAnalysis().getHistoricalDays());

            // Aggregate data
            RideStatistics stats = aggregateRideData(completedRides);

            // Build AI prompt with aggregated data
            String prompt = buildPatternAnalysisPrompt(stats);

            // Query AI
            String aiResponse = aiApiService.queryAi(getSystemPrompt(), prompt);
            log.debug("AI pattern analysis response: {}", aiResponse);

            // Parse AI insights
            RidePatterns patterns = parsePatternInsights(aiResponse, stats);

            // Cache results
            cachedPatterns = patterns;
            lastAnalysisTime = LocalDateTime.now();

            log.info("=== AI Pattern Analysis Complete ===");
            log.info("Popular routes identified: {}", patterns.getPopularRoutes().size());
            log.info("Peak hours identified: {}", patterns.getPeakHours().size());
            log.info("Underserved routes: {}", patterns.getUnderservedRoutes().size());

        } catch (AiServiceException e) {
            log.error("AI pattern analysis failed: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during pattern analysis: {}", e.getMessage(), e);
        }
    }

    /**
     * Aggregate ride data into statistics.
     */
    private RideStatistics aggregateRideData(List<SharedRide> rides) {
        RideStatistics stats = new RideStatistics();

        for (SharedRide ride : rides) {
            // Route frequency
            String routeKey = formatRoute(
                ride.getStartLocation().getName(),
                ride.getEndLocation().getName()
            );
            stats.routeFrequency.merge(routeKey, 1, Integer::sum);

            // Hour distribution
            int hour = ride.getScheduledTime().getHour();
            stats.hourFrequency.merge(hour, 1, Integer::sum);

            // Day of week distribution
            DayOfWeek day = ride.getScheduledTime().getDayOfWeek();
            stats.dayFrequency.merge(day, 1, Integer::sum);

            // Count shared rides (rides that had a shared_ride_request)
            if (ride.getSharedRideRequest() != null) {
                stats.sharedRideCount++;
                stats.totalPassengers++; // At least one passenger
            }
        }

        stats.totalRides = rides.size();
        stats.averagePassengersPerRide = stats.totalRides > 0 ?
            (double) stats.totalPassengers / stats.totalRides : 0.0;

        return stats;
    }

    /**
     * Build prompt for AI pattern analysis.
     */
    private String buildPatternAnalysisPrompt(RideStatistics stats) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(String.format("""
            Analyze these FPT University student ride-sharing patterns from the last %d days:
            
            TOTAL RIDES: %d
            SHARED RIDES: %d (%.1f%%)
            AVERAGE PASSENGERS PER RIDE: %.2f
            
            TOP 10 ROUTES BY FREQUENCY:
            """,
            aiConfig.getPatternAnalysis().getHistoricalDays(),
            stats.totalRides,
            stats.sharedRideCount,
            stats.totalRides > 0 ? (stats.sharedRideCount * 100.0 / stats.totalRides) : 0,
            stats.averagePassengersPerRide
        ));

        // Add route frequencies
        stats.routeFrequency.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> prompt.append(String.format("- %s: %d rides\n", 
                entry.getKey(), entry.getValue())));

        prompt.append("\nHOURLY DISTRIBUTION:\n");
        stats.hourFrequency.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> prompt.append(String.format("- %02d:00: %d rides\n",
                entry.getKey(), entry.getValue())));

        prompt.append("\nDAY OF WEEK DISTRIBUTION:\n");
        stats.dayFrequency.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> prompt.append(String.format("- %s: %d rides\n",
                entry.getKey(), entry.getValue())));

        prompt.append("""
            
            ANALYSIS TASKS:
            1. Identify the top 5 most popular campus routes (to/from FPT University)
            2. Determine 3-5 peak commute time slots
            3. Identify 3-5 underserved routes or times that should be promoted
            4. Suggest optimal driver positioning strategies
            5. Recommend times when drivers should offer rides to maximize bookings
            
            FORMAT YOUR RESPONSE AS:
            POPULAR_ROUTES: route1 | route2 | route3 | route4 | route5
            PEAK_HOURS: hour1,hour2,hour3
            UNDERSERVED_ROUTES: route1 | route2 | route3
            DRIVER_RECOMMENDATIONS: recommendation text here
            """);

        return prompt.toString();
    }

    /**
     * System prompt for pattern analysis.
     */
    private String getSystemPrompt() {
        return """
            You are an AI data analyst for a university campus motorbike ride-sharing system.
            Your role is to identify patterns in student commute behavior.
            Focus on:
            - Routes to/from FPT University campus
            - Morning rush (7-9 AM) and evening rush (4-6 PM) patterns
            - Weekday vs weekend differences
            - Opportunities to improve service coverage
            
            Provide actionable insights that help optimize the ride-sharing service.
            """;
    }

    /**
     * Parse AI response into structured pattern data.
     */
    private RidePatterns parsePatternInsights(String aiResponse, RideStatistics stats) {
        RidePatterns patterns = new RidePatterns();

        try {
            String[] lines = aiResponse.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("POPULAR_ROUTES:")) {
                    String routesStr = line.substring("POPULAR_ROUTES:".length()).trim();
                    patterns.popularRoutes = Arrays.stream(routesStr.split("\\|"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(route -> new PopularRoute(route, getRouteFrequency(stats, route), 1.15))
                        .collect(Collectors.toList());
                }
                else if (line.startsWith("PEAK_HOURS:")) {
                    String hoursStr = line.substring("PEAK_HOURS:".length()).trim();
                    patterns.peakHours = Arrays.stream(hoursStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
                }
                else if (line.startsWith("UNDERSERVED_ROUTES:")) {
                    String routesStr = line.substring("UNDERSERVED_ROUTES:".length()).trim();
                    patterns.underservedRoutes = Arrays.stream(routesStr.split("\\|"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                }
                else if (line.startsWith("DRIVER_RECOMMENDATIONS:")) {
                    patterns.driverRecommendations = line.substring("DRIVER_RECOMMENDATIONS:".length()).trim();
                }
            }

            log.info("Successfully parsed AI pattern insights");

        } catch (Exception e) {
            log.error("Failed to parse AI pattern insights: {}", e.getMessage());
            // Return empty patterns on parse error
            patterns.popularRoutes = new ArrayList<>();
            patterns.peakHours = new ArrayList<>();
            patterns.underservedRoutes = new ArrayList<>();
            patterns.driverRecommendations = "Analysis in progress";
        }

        return patterns;
    }

    private int getRouteFrequency(RideStatistics stats, String route) {
        return stats.routeFrequency.getOrDefault(route, 0);
    }

    private String formatRoute(String start, String end) {
        return String.format("%s → %s", start, end);
    }

    /**
     * Get cached pattern analysis results.
     */
    public Optional<RidePatterns> getCachedPatterns() {
        if (cachedPatterns == null) {
            return Optional.empty();
        }

        // Check if cache is still valid
        int cacheHours = aiConfig.getPatternAnalysis().getCacheHours();
        if (lastAnalysisTime != null && 
            lastAnalysisTime.plusHours(cacheHours).isAfter(LocalDateTime.now())) {
            return Optional.of(cachedPatterns);
        }

        return Optional.empty();
    }

    /**
     * Check if route is identified as popular by AI.
     */
    public Optional<Double> getRouteBoostFactor(String startLocation, String endLocation) {
        return getCachedPatterns()
            .flatMap(patterns -> patterns.getPopularRoutes().stream()
                .filter(route -> route.getRouteName().contains(startLocation) && 
                                route.getRouteName().contains(endLocation))
                .findFirst()
                .map(PopularRoute::getBoostFactor));
    }

    /**
     * Check if time is identified as peak hour.
     */
    public boolean isPeakHour(int hour) {
        return getCachedPatterns()
            .map(patterns -> patterns.getPeakHours().contains(hour))
            .orElse(false);
    }

    // Data classes
    private static class RideStatistics {
        Map<String, Integer> routeFrequency = new HashMap<>();
        Map<Integer, Integer> hourFrequency = new HashMap<>();
        Map<DayOfWeek, Integer> dayFrequency = new HashMap<>();
        int totalRides = 0;
        int totalPassengers = 0;
        int sharedRideCount = 0;
        double averagePassengersPerRide = 0.0;
    }

    public static class RidePatterns {
        private List<PopularRoute> popularRoutes = new ArrayList<>();
        private List<Integer> peakHours = new ArrayList<>();
        private List<String> underservedRoutes = new ArrayList<>();
        private String driverRecommendations = "";

        public List<PopularRoute> getPopularRoutes() {
            return popularRoutes;
        }

        public List<Integer> getPeakHours() {
            return peakHours;
        }

        public List<String> getUnderservedRoutes() {
            return underservedRoutes;
        }

        public String getDriverRecommendations() {
            return driverRecommendations;
        }
    }

    public static class PopularRoute {
        private final String routeName;
        private final int frequency;
        private final double boostFactor;

        public PopularRoute(String routeName, int frequency, double boostFactor) {
            this.routeName = routeName;
            this.frequency = frequency;
            this.boostFactor = boostFactor;
        }

        public String getRouteName() {
            return routeName;
        }

        public int getFrequency() {
            return frequency;
        }

        public double getBoostFactor() {
            return boostFactor;
        }
    }
}

