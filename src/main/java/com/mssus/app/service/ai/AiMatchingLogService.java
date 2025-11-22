package com.mssus.app.service.ai;

import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.AiMatchingLog;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.repository.AiMatchingLogRepository;
import com.mssus.app.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persists AI matching results in their own transaction to avoid read-only caller issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiMatchingLogService {

    private final AiMatchingLogRepository aiLogRepository;
    private final DriverProfileRepository driverRepository;
    private final AiApiService aiApiService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logMatchingDecision(
            SharedRideRequest request,
            List<RideMatchProposalResponse> originalCandidates,
            List<RideMatchProposalResponse> aiRankedCandidates,
            String aiResponse,
            boolean success,
            String failureReason,
            long processingTimeMs) {

        try {
            RideMatchProposalResponse selectedMatch = aiRankedCandidates.isEmpty() ?
                null : aiRankedCandidates.get(0);

            Optional<DriverProfile> selectedDriver = selectedMatch != null ?
                driverRepository.findById(selectedMatch.getDriverId()) : Optional.empty();

            String matchingFactors = formatMatchingFactors(originalCandidates, aiRankedCandidates);
            String potentialMatches = formatPotentialMatches(originalCandidates, aiRankedCandidates, aiResponse);

            AiMatchingLog logEntry = AiMatchingLog.builder()
                .sharedRideRequest(request)
                .selectedDriver(selectedDriver.orElse(null))
                .algorithmVersion("ai-hybrid-v1")
                .requestLocation(formatRequestLocation(request))
                .searchRadiusKm(2.0f) // From matching config
                .availableDriversCount(originalCandidates.size())
                .matchingFactors(matchingFactors)
                .potentialMatches(potentialMatches)
                .matchingScore(selectedMatch != null ? selectedMatch.getMatchScore() : null)
                .processingTimeMs((int) processingTimeMs)
                .success(success)
                .failureReason(failureReason)
                .build();

            aiLogRepository.save(logEntry);

            log.debug("AI matching decision logged for request {}", request.getSharedRideRequestId());

        } catch (Exception e) {
            log.error("Failed to log AI matching decision: {}", e.getMessage(), e);
            // Swallow to avoid impacting the calling flow
        }
    }

    private String formatRequestLocation(SharedRideRequest request) {
        return String.format("%.6f,%.6f -> %.6f,%.6f",
            request.getPickupLocation().getLat(),
            request.getPickupLocation().getLng(),
            request.getDropoffLocation().getLat(),
            request.getDropoffLocation().getLng());
    }

    private String formatMatchingFactors(
            List<RideMatchProposalResponse> original,
            List<RideMatchProposalResponse> aiRanked) {

        if (original.isEmpty()) return "No candidates";

        return String.format("Candidates: %d, AI reranking: %s, Provider: %s",
            original.size(),
            aiRanked.isEmpty() ? "failed" : "success",
            aiApiService.getProviderInfo());
    }

    private String formatPotentialMatches(
            List<RideMatchProposalResponse> original,
            List<RideMatchProposalResponse> aiRanked,
            String aiResponse) {

        StringBuilder sb = new StringBuilder();

        sb.append("Original top 3: ");
        for (int i = 0; i < Math.min(3, original.size()); i++) {
            RideMatchProposalResponse p = original.get(i);
            sb.append(String.format("D%d(%.1f) ", p.getDriverId(), p.getMatchScore()));
        }

        if (!aiRanked.isEmpty() && !original.equals(aiRanked)) {
            sb.append("| AI top 3: ");
            for (int i = 0; i < Math.min(3, aiRanked.size()); i++) {
                RideMatchProposalResponse p = aiRanked.get(i);
                sb.append(String.format("D%d(%.1f) ", p.getDriverId(), p.getMatchScore()));
            }
        }

        if (aiResponse != null && aiResponse.length() > 0) {
            sb.append(String.format("| AI raw: %s",
                aiResponse.length() > 50 ? aiResponse.substring(0, 50) + "..." : aiResponse));
        }

        return sb.toString();
    }
}
