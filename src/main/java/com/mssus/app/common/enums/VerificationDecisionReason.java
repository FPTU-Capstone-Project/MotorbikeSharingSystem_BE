package com.mssus.app.common.enums;

/**
 * Canonical set of decision reasons captured during verification reviews.
 */
public enum VerificationDecisionReason {
    DOCUMENT_MATCH,
    DOCUMENT_MISMATCH,
    FRAUD_SUSPECTED,
    INFORMATION_INCOMPLETE,
    EXPIRED_DOCUMENT,
    MANUAL_OVERRIDE,
    OTHER
}
