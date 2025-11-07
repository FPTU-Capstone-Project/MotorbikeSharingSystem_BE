package com.mssus.app.service.domain.matching.session;

import java.time.Duration;
import java.util.Optional;

public interface MatchingSessionRepository {

    Optional<MatchingSessionState> find(Integer requestId);

    void save(MatchingSessionState state, Duration ttl);

    void delete(Integer requestId);
}
