package com.mssus.app.service.domain.verification;

import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.entity.Verification;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class VerificationRiskEvaluator {

    private static final int HIGH_RISK_THRESHOLD = 70;

    public int calculateScore(Verification verification) {
        int base = switch (verification.getType()) {
            case BACKGROUND_CHECK -> 90;
            case DRIVER_DOCUMENTS, DRIVER_LICENSE, VEHICLE_REGISTRATION -> 80;
            case STUDENT_ID -> 45;
            default -> 50;
        };

        if (verification.getMetadata() != null) {
            String metadataLower = verification.getMetadata().toLowerCase(Locale.ROOT);
            if (metadataLower.contains("mismatch") || metadataLower.contains("fraud")) {
                base += 15;
            }
            if (metadataLower.contains("manual_review")) {
                base += 10;
            }
        }

        if (verification.getDocumentUrl() != null && verification.getDocumentUrl().contains("reupload")) {
            base += 5;
        }

        return Math.min(base, 100);
    }

    public boolean isHighRisk(int score) {
        return score >= HIGH_RISK_THRESHOLD;
    }
}
