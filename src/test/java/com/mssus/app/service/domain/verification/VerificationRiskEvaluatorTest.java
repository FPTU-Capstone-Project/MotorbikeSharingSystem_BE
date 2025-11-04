package com.mssus.app.service.domain.verification;

import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Verification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationRiskEvaluatorTest {

    private final VerificationRiskEvaluator evaluator = new VerificationRiskEvaluator();

    @Test
    void calculateScore_shouldElevateForDriverDocumentsAndSuspiciousMetadata() {
        Verification verification = Verification.builder()
                .verificationId(1)
                .user(new User())
                .type(VerificationType.DRIVER_LICENSE)
                .status(VerificationStatus.PENDING)
                .metadata("{\"flags\":[\"manual_review\",\"possible_mismatch\"]}")
                .documentUrl("https://cdn/app/driver/reupload.png")
                .build();

        int score = evaluator.calculateScore(verification);

        assertThat(score).isGreaterThanOrEqualTo(95);
        assertThat(evaluator.isHighRisk(score)).isTrue();
    }

    @Test
    void calculateScore_shouldRemainLowForStudentIdWithoutSignals() {
        Verification verification = Verification.builder()
                .verificationId(2)
                .user(new User())
                .type(VerificationType.STUDENT_ID)
                .status(VerificationStatus.PENDING)
                .metadata("{\"auto_check\":\"pass\"}")
                .documentUrl("https://cdn/app/student/initial.png")
                .build();

        int score = evaluator.calculateScore(verification);

        assertThat(score).isLessThan(70);
        assertThat(evaluator.isHighRisk(score)).isFalse();
    }
}
