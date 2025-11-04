package com.mssus.app.entity;

import com.mssus.app.common.enums.VerificationDecisionOutcome;
import com.mssus.app.common.enums.VerificationDecisionReason;
import com.mssus.app.common.enums.VerificationReviewStage;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_decisions", indexes = {
        @Index(name = "idx_decision_verification_stage", columnList = "verification_id, review_stage"),
        @Index(name = "idx_decision_reviewer", columnList = "reviewer_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class VerificationDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false)
    private Verification verification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_stage", nullable = false, length = 20)
    private VerificationReviewStage reviewStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 30)
    private VerificationDecisionOutcome outcome;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_reason", length = 100)
    private VerificationDecisionReason decisionReason;

    @Column(name = "decision_notes", columnDefinition = "TEXT")
    private String decisionNotes;

    @Column(name = "evidence_references", columnDefinition = "TEXT")
    private String evidenceReferences;

    @Column(name = "document_annotations", columnDefinition = "TEXT")
    private String documentAnnotations;

    @Column(name = "override_justification", columnDefinition = "TEXT")
    private String overrideJustification;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
