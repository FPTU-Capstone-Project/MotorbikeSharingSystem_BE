package com.mssus.app.common.enums;

/**
 * Enumerates the different types of audit events captured for verification reviews.
 */
public enum VerificationAuditEventType {
    QUEUE_ASSIGNMENT,
    STATUS_CHANGED,
    DECISION_RECORDED,
    SECONDARY_REVIEW_TRIGGERED,
    SECONDARY_REVIEW_COMPLETED,
    OVERRIDE_APPLIED,
    ESCALATION_NOTIFIED
}
