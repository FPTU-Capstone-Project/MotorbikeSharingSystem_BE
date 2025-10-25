package com.mssus.app.service.domain.matching;

import com.mssus.app.dto.response.ride.RideMatchProposalResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin selector for ranked ride proposals.
 *
 * <p>The matching algorithm returns candidates sorted by score. The selector
 * guarantees each candidate is returned at most once in the original order.</p>
 */
public class CandidateSelector {

    private final List<RideMatchProposalResponse> proposals;
    private final AtomicInteger index = new AtomicInteger(0);

    public CandidateSelector(List<RideMatchProposalResponse> proposals) {
        this.proposals = proposals == null ? List.of() : List.copyOf(proposals);
    }

    public boolean hasNext() {
        return index.get() < proposals.size();
    }

    public Optional<RideMatchProposalResponse> next() {
        int current = index.getAndIncrement();
        if (current >= proposals.size()) {
            return Optional.empty();
        }
        return Optional.of(proposals.get(current));
    }

    public List<RideMatchProposalResponse> asList() {
        return Collections.unmodifiableList(proposals);
    }

    public int size() {
        return proposals.size();
    }
}

