package com.mssus.app.repository;

import com.mssus.app.common.enums.VerificationReviewAssignmentStatus;
import com.mssus.app.entity.Verification;
import com.mssus.app.entity.VerificationReviewAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationReviewAssignmentRepository extends JpaRepository<VerificationReviewAssignment, Long> {

    Optional<VerificationReviewAssignment> findFirstByVerificationAndStatus(Verification verification,
                                                                           VerificationReviewAssignmentStatus status);

    Optional<VerificationReviewAssignment> findByVerification_VerificationIdAndStatus(Integer verificationId,
                                                                                      VerificationReviewAssignmentStatus status);

    List<VerificationReviewAssignment> findByReviewer_UserIdAndStatus(Integer reviewerId,
                                                                      VerificationReviewAssignmentStatus status);
}
