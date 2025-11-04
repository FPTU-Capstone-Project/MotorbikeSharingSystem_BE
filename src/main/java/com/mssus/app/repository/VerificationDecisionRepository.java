package com.mssus.app.repository;

import com.mssus.app.common.enums.VerificationReviewStage;
import com.mssus.app.entity.VerificationDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationDecisionRepository extends JpaRepository<VerificationDecision, Long> {

    List<VerificationDecision> findByVerification_VerificationIdOrderByCreatedAtAsc(Integer verificationId);

    Optional<VerificationDecision> findFirstByVerification_VerificationIdAndReviewStageOrderByCreatedAtDesc(
            Integer verificationId,
            VerificationReviewStage reviewStage);
}
