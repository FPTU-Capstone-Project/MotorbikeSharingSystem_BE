package com.mssus.app.service.ai;

import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.appconfig.config.properties.AiConfigurationProperties;
import com.mssus.app.service.ai.AiApiService.AiServiceException;
import com.mssus.app.service.ai.AiMatchingLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * AI-powered ride matching service.
 * Uses AI to analyze candidates and make final ranking decisions.
 * This satisfies the requirement: "The system shall use AI algorithms to match
 * riders with drivers"
 *
 * @since 1.0.0 (AI Integration)
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiMatchingService {

    private final AiApiService aiApiService;
    private final AiConfigurationProperties aiConfig;
    private final AiMatchingLogService aiMatchingLogService;

    /**
     * AI analyzes all candidates and makes final ranking decision.
     * This is the core method that replaces weighted-sum algorithm with AI
     * decision-making.
     *
     * @param request    The ride request from the rider
     * @param candidates List of candidate drivers from base algorithm
     * @return AI-ranked list of proposals
     */
    public List<RideMatchProposalResponse> aiRankMatches(
            SharedRideRequest request,
            List<RideMatchProposalResponse> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            log.debug("No candidates to rank");
            return candidates;
        }

        // Limit candidates sent to AI to reduce costs
        List<RideMatchProposalResponse> candidatesForAi = candidates.stream()
                .limit(aiConfig.getMaxCandidatesForAi())
                .collect(Collectors.toList());

        log.info("AI ranking {} candidates for request {}",
                candidatesForAi.size(), request.getSharedRideRequestId());

        long startTime = System.currentTimeMillis();
        String aiResponse = null;

        try {
            // Build comprehensive context for AI
            String prompt = buildMatchingDecisionPrompt(request, candidatesForAi);

            // Call AI API
            aiResponse = aiApiService.queryAi(getSystemPrompt(), prompt);
            log.debug("AI response: {}", aiResponse);

            // Parse AI's ranking decision
            List<Integer> rankedIndices = parseRankingResponse(aiResponse, candidatesForAi.size());

            // Reorder proposals based on AI decision
            List<RideMatchProposalResponse> aiRankedProposals = reorderByAiDecision(candidatesForAi, rankedIndices);

            // Log the successful AI decision
            aiMatchingLogService.logMatchingDecision(request, candidatesForAi, aiRankedProposals,
                    aiResponse, true, null,
                    System.currentTimeMillis() - startTime);

            log.info("AI successfully ranked candidates. Top match: Driver {} (score: {})",
                    aiRankedProposals.get(0).getDriverId(),
                    aiRankedProposals.get(0).getMatchScore());

            return aiRankedProposals;

        } catch (AiServiceException e) {
            log.error("AI ranking failed: {}", e.getMessage());

            // Try to log the failure (in a separate transaction)
            aiMatchingLogService.logMatchingDecision(request, candidatesForAi, candidates,
                    aiResponse, false, e.getMessage(),
                    System.currentTimeMillis() - startTime);

            // Fallback to original ranking
            if (aiConfig.isFallbackToBaseAlgorithm()) {
                log.info("Falling back to base algorithm ranking");
                return candidates;
            } else {
                throw e;
            }
        }
    }

    /**
     * Build comprehensive prompt with all context AI needs to make ranking
     * decision.
     */
    private String buildMatchingDecisionPrompt(
            SharedRideRequest request,
            List<RideMatchProposalResponse> candidates) {

        LocalDateTime requestTime = request.getPickupTime();
        DayOfWeek dayOfWeek = requestTime.getDayOfWeek();
        int hour = requestTime.getHour();
        String timeOfDay = getTimeOfDayDescription(hour);

        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("""
                Analyze these ride-sharing candidates for FPT University student commute and rank them.

                RIDER REQUEST:
                - Pickup: %s (%.6f, %.6f)
                - Dropoff: %s (%.6f, %.6f) [Must involve FPT Campus]
                - Time: %s, %s (%02d:00)
                - Requested fare: %s VND

                CANDIDATES (%d drivers available):
                """,
                request.getPickupLocation().getName(),
                request.getPickupLocation().getLat(),
                request.getPickupLocation().getLng(),
                request.getDropoffLocation().getName(),
                request.getDropoffLocation().getLat(),
                request.getDropoffLocation().getLng(),
                dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                timeOfDay,
                hour,
                request.getTotalFare(),
                candidates.size()));

        // Add each candidate with full details
        for (int i = 0; i < candidates.size(); i++) {
            RideMatchProposalResponse candidate = candidates.get(i);
            prompt.append(String.format("""

                    [%d] Driver: %s | Rating: %.1f/5 | Vehicle: %s %s
                        Pickup distance: %.2f km | Detour: %d min
                        Base score: %.1f/100 | Scheduled: %s
                        Estimated pickup: %s | Fare: %s VND
                    """,
                    i + 1,
                    candidate.getDriverName(),
                    candidate.getDriverRating(),
                    candidate.getVehicleModel(),
                    candidate.getVehiclePlate(),
                    candidate.getDetourDistance(),
                    candidate.getDetourDuration(),
                    candidate.getMatchScore(),
                    candidate.getScheduledTime(),
                    candidate.getEstimatedPickupTime(),
                    candidate.getTotalFare()));
        }

        prompt.append("""

                RANKING CRITERIA:
                1. Proximity: Driver heading to/from FPT campus, pickup within reasonable distance
                2. Time compatibility: Driver schedule matches rider's time
                3. Route overlap: Higher shared path is better
                4. Driver reliability: Rating and experience
                5. Safety: Prioritize high ratings for late rides (after 10 PM)
                6. Campus patterns: Consider if this is peak commute time (7-9 AM, 4-6 PM)

                TASK: Rank candidates from best to worst.
                Return ONLY comma-separated numbers (e.g., "3,1,4,2" means candidate 3 is best, then 1, then 4, then 2).
                """);

        return prompt.toString();
    }

    /**
     * System prompt that defines AI's role and constraints.
     */
    private String getSystemPrompt() {
        return """
                You are an AI matching algorithm for a university campus motorbike ride-sharing system.
                Students use this to commute to/from FPT University in Ho Chi Minh City.

                Your role:
                - Analyze ride candidates considering proximity, timing, safety, and student patterns
                - Prioritize student safety (higher ratings for night rides)
                - Consider campus commute patterns (morning rush 7-9 AM, evening 4-6 PM)
                - Make decisions that balance convenience and safety

                Response format: Return ONLY the ranking as comma-separated numbers.
                Example: "2,1,3" means candidate 2 is best, candidate 1 second, candidate 3 third.
                """;
    }

    /**
     * Parse AI's ranking response into list of indices.
     */
    private List<Integer> parseRankingResponse(String response, int numCandidates) {
        log.debug("Parsing AI ranking response: {}", response);

        // Clean response - keep only numbers and commas
        String cleaned = response.trim()
                .replaceAll("[^0-9,]", "")
                .replaceAll("\\s+", "");

        List<Integer> ranking = new ArrayList<>();

        if (cleaned.isEmpty()) {
            log.warn("Empty AI response after cleaning, using original order");
            for (int i = 0; i < numCandidates; i++) {
                ranking.add(i);
            }
            return ranking;
        }

        String[] parts = cleaned.split(",");

        for (String part : parts) {
            if (part.isEmpty())
                continue;

            try {
                int candidateNum = Integer.parseInt(part.trim());
                int index = candidateNum - 1; // Convert to 0-based index

                if (index >= 0 && index < numCandidates && !ranking.contains(index)) {
                    ranking.add(index);
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse ranking part: {}", part);
            }
        }

        // Add any missing indices to the end (fallback)
        for (int i = 0; i < numCandidates; i++) {
            if (!ranking.contains(i)) {
                ranking.add(i);
                log.debug("Adding missing candidate {} to end of ranking", i + 1);
            }
        }

        log.debug("Parsed ranking: {}", ranking.stream()
                .map(i -> String.valueOf(i + 1))
                .collect(Collectors.joining(",")));

        return ranking;
    }

    /**
     * Reorder proposals based on AI's decision.
     * Updates match scores to reflect new ranking.
     */
    private List<RideMatchProposalResponse> reorderByAiDecision(
            List<RideMatchProposalResponse> original,
            List<Integer> aiRanking) {

        List<RideMatchProposalResponse> reordered = new ArrayList<>();

        for (int rank = 0; rank < aiRanking.size(); rank++) {
            Integer originalIndex = aiRanking.get(rank);
            if (originalIndex < original.size()) {
                RideMatchProposalResponse proposal = original.get(originalIndex);

                // Update match score to reflect AI ranking
                // Top candidate gets 100, each subsequent gets 5 points less
                float newScore = 100.0f - (rank * 5.0f);
                proposal.setMatchScore(Math.max(newScore, 50.0f)); // Minimum 50

                reordered.add(proposal);

                log.debug("AI rank {}: Driver {} (original score: {} -> AI score: {})",
                        rank + 1, proposal.getDriverId(),
                        original.get(originalIndex).getMatchScore(), newScore);
            }
        }

        return reordered;
    }

    private String getTimeOfDayDescription(int hour) {
        if (hour >= 6 && hour < 9)
            return "Morning Rush";
        if (hour >= 9 && hour < 12)
            return "Late Morning";
        if (hour >= 12 && hour < 14)
            return "Lunch Hour";
        if (hour >= 14 && hour < 17)
            return "Afternoon";
        if (hour >= 17 && hour < 20)
            return "Evening Rush";
        if (hour >= 20 && hour < 23)
            return "Evening";
        return "Night";
    }

    /**
     * Check if AI matching is available and configured.
     */
    public boolean isAvailable() {
        return aiApiService.isAvailable();
    }
}
