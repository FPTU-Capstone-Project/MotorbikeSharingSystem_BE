package com.mssus.app.service.domain.matching.session;

public enum MatchingSessionPhase {
    MATCHING,
    AWAITING_CONFIRMATION,
    BROADCASTING,
    COMPLETED,
    EXPIRED,
    CANCELLED
}
