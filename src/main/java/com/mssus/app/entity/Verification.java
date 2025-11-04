package com.mssus.app.entity;

import com.mssus.app.common.enums.DocumentType;
import com.mssus.app.common.enums.VerificationReviewStage;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "verifications", indexes = {
    @Index(name = "idx_user_type_status", columnList = "user_id, type, status"),
    @Index(name = "idx_status_created", columnList = "status, created_at"),
    @Index(name = "idx_verification_queue_priority", columnList = "status, high_risk, risk_score, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Integer verificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private VerificationType type; // student_id, driver_license, background_check, vehicle_registration

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING; // pending, approved, rejected, expired

    @Column(name = "document_url")
    private String documentUrl;

    @Column(name = "document_type")
    @Enumerated(EnumType.STRING)
    private DocumentType documentType; // image, pdf

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by", referencedColumnName = "user_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON data for additional verification info

    @Column(name = "risk_score")
    @Builder.Default
    private Integer riskScore = 0;

    @Column(name = "high_risk")
    @Builder.Default
    private Boolean highRisk = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_reviewer_id", referencedColumnName = "user_id")
    private User assignedReviewer;

    @Column(name = "assignment_claimed_at")
    private LocalDateTime assignmentClaimedAt;

    @Column(name = "secondary_review_required")
    @Builder.Default
    private Boolean secondaryReviewRequired = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_review_stage", length = 30)
    @Builder.Default
    private VerificationReviewStage currentReviewStage = VerificationReviewStage.PRIMARY;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VerificationReviewAssignment> reviewAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VerificationDecision> decisions = new ArrayList<>();

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VerificationAuditLog> auditLogs = new ArrayList<>();
}
