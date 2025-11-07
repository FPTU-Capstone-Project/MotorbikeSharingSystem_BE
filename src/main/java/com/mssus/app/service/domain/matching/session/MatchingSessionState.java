package com.mssus.app.service.domain.matching.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingSessionState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer requestId;
    private MatchingSessionPhase phase;
    private List<RideMatchProposalResponse> proposals;
    private int nextProposalIndex;
    private ActiveOfferState activeOffer;
    private Set<Integer> notifiedDrivers;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant requestDeadline;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant broadcastDeadline;
    
    private String lastProcessedMessageId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastProcessedAt;

    public static MatchingSessionState initialize(Integer requestId,
                                                  Instant deadline,
                                                  List<RideMatchProposalResponse> proposals) {
        return MatchingSessionState.builder()
            .requestId(requestId)
            .phase(MatchingSessionPhase.MATCHING)
            .proposals(new ArrayList<>(proposals))
            .nextProposalIndex(0)
            .activeOffer(null)
            .requestDeadline(deadline)
            .notifiedDrivers(new HashSet<>())
            .build();
    }

    public boolean hasMoreProposals() {
        return proposals != null && nextProposalIndex < proposals.size();
    }

    public RideMatchProposalResponse consumeNextProposal() {
        if (!hasMoreProposals()) {
            return null;
        }
        RideMatchProposalResponse proposal = proposals.get(nextProposalIndex);
        nextProposalIndex++;
        return proposal;
    }

    public void recordNotifiedDriver(int driverId) {
        if (notifiedDrivers == null) {
            notifiedDrivers = new HashSet<>();
        }
        notifiedDrivers.add(driverId);
    }

    public boolean wasDriverNotified(Integer driverId) {
        return notifiedDrivers != null && driverId != null && notifiedDrivers.contains(driverId);
    }

    public boolean isBroadcasting() {
        return phase == MatchingSessionPhase.BROADCASTING;
    }

    public boolean isTerminal() {
        return phase == MatchingSessionPhase.COMPLETED || phase == MatchingSessionPhase.EXPIRED;
    }

    public void markCompleted() {
        phase = MatchingSessionPhase.COMPLETED;
        activeOffer = null;
    }

    public void markExpired() {
        phase = MatchingSessionPhase.EXPIRED;
        activeOffer = null;
    }

    public void markCancelled() {
        phase = MatchingSessionPhase.CANCELLED;
        activeOffer = null;
    }

    public void enterBroadcast(Instant deadline) {
        phase = MatchingSessionPhase.BROADCASTING;
        broadcastDeadline = deadline;
        activeOffer = null;
    }

    public boolean shouldProcess(String messageId) {
        if (messageId == null) {
            return true; // Allow processing if no message ID provided
        }
        
        if (messageId.equals(lastProcessedMessageId)) {
            return false; // Duplicate message
        }
        
        // Update last processed message
        lastProcessedMessageId = messageId;
        lastProcessedAt = Instant.now();
        return true;
    }
}
