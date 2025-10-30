package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.enums.VerificationAuditEventType;
import com.mssus.app.common.enums.VerificationDecisionOutcome;
import com.mssus.app.common.enums.VerificationDecisionReason;
import com.mssus.app.common.enums.VerificationReviewAssignmentStatus;
import com.mssus.app.common.enums.VerificationReviewStage;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.dto.request.VerificationReviewDecisionRequest;
import com.mssus.app.dto.request.VerificationReviewDocumentAnnotation;
import com.mssus.app.dto.response.VerificationAssignmentResponse;
import com.mssus.app.dto.response.VerificationDecisionResponse;
import com.mssus.app.dto.response.VerificationQueueItemResponse;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Verification;
import com.mssus.app.entity.VerificationAuditLog;
import com.mssus.app.entity.VerificationDecision;
import com.mssus.app.entity.VerificationReviewAssignment;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VerificationAuditLogRepository;
import com.mssus.app.repository.VerificationDecisionRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.repository.VerificationReviewAssignmentRepository;
import com.mssus.app.service.AnalyticsService;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.domain.verification.VerificationOutcomeHandler;
import com.mssus.app.service.domain.verification.VerificationRiskEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationReviewServiceImplTest {

    private static final String REVIEWER_EMAIL = "reviewer@campus.edu";
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 1, 15, 10, 30);

    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VerificationReviewAssignmentRepository assignmentRepository;
    @Mock
    private VerificationDecisionRepository decisionRepository;
    @Mock
    private VerificationAuditLogRepository auditLogRepository;
    @Mock
    private VerificationOutcomeHandler verificationOutcomeHandler;
    @Mock
    private VerificationRiskEvaluator verificationRiskEvaluator;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private VerificationReviewServiceImpl verificationReviewService;

    private User reviewer;
    private User applicant;

    @BeforeEach
    void setUp() throws Exception {
        reviewer = buildUser(1000, REVIEWER_EMAIL, "Alex Reviewer", UserType.ADMIN);
        applicant = buildUser(2000, "applicant@uni.edu", "Riley Student", UserType.USER);

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        lenient().when(userRepository.findByEmailWithProfiles(REVIEWER_EMAIL)).thenReturn(Optional.of(reviewer));
    }

    @Test
    void decide_shouldEscalateHighRiskPrimaryApproval() {
        Verification verification = Verification.builder()
                .verificationId(1)
                .user(applicant)
                .status(VerificationStatus.IN_REVIEW)
                .highRisk(true)
                .riskScore(80)
                .currentReviewStage(VerificationReviewStage.PRIMARY)
                .assignedReviewer(reviewer)
                .build();

        when(verificationRepository.findById(1)).thenReturn(Optional.of(verification));
        when(verificationRiskEvaluator.calculateScore(verification)).thenReturn(82);
        when(verificationRiskEvaluator.isHighRisk(82)).thenReturn(true);
        when(verificationRepository.save(verification)).thenReturn(verification);
        when(decisionRepository.save(any(VerificationDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByUserType(UserType.ADMIN)).thenReturn(List.of(buildUser(3000, "lead@uni.edu", "Dana Lead", UserType.ADMIN)));
        when(assignmentRepository.findByVerification_VerificationIdAndStatus(eq(1), eq(VerificationReviewAssignmentStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        VerificationReviewDecisionRequest request = VerificationReviewDecisionRequest.builder()
                .verificationId(1)
                .outcome(VerificationDecisionOutcome.APPROVED)
                .decisionReason(VerificationDecisionReason.DOCUMENT_MATCH)
                .decisionNotes("Looks good")
                .annotations(List.of(VerificationReviewDocumentAnnotation.builder()
                        .page(1)
                        .x(10.0)
                        .y(20.0)
                        .width(30.0)
                        .height(15.0)
                        .comment("missing seal")
                        .build()))
                .build();

        VerificationDecisionResponse response = verificationReviewService.decide(REVIEWER_EMAIL, request);

        assertThat(response.getStatus()).isEqualTo(VerificationStatus.AWAITING_SECONDARY_REVIEW.name());
        assertThat(response.isSecondaryReviewRequired()).isTrue();
        assertThat(response.getStage()).isEqualTo(VerificationReviewStage.SECONDARY);

        assertThat(verification.getStatus()).isEqualTo(VerificationStatus.AWAITING_SECONDARY_REVIEW);
        assertThat(verification.getAssignedReviewer()).isNull();
        assertThat(verification.getSecondaryReviewRequired()).isTrue();

        verify(verificationRepository).save(verification);
        verify(verificationOutcomeHandler, never()).handleApproval(any());
        verify(notificationService).sendNotification(
                any(User.class),
                eq(NotificationType.VERIFICATION_ESCALATED),
                anyString(),
                anyString(),
                anyString(),
                eq(Priority.HIGH),
                eq(com.mssus.app.common.enums.DeliveryMethod.IN_APP),
                eq("verification")
        );
        verify(analyticsService).trackVerificationEscalation(eq(verification), anyMap());

        ArgumentCaptor<VerificationAuditLog> auditCaptor = ArgumentCaptor.forClass(VerificationAuditLog.class);
        verify(auditLogRepository, atLeast(3)).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(VerificationAuditLog::getEventType)
                .contains(
                        VerificationAuditEventType.DECISION_RECORDED,
                        VerificationAuditEventType.SECONDARY_REVIEW_TRIGGERED,
                        VerificationAuditEventType.ESCALATION_NOTIFIED
                );
    }

    @Test
    void decide_shouldCompleteSecondaryApprovalAndInvokeOutcomeHandler() {
        Verification verification = Verification.builder()
                .verificationId(2)
                .user(applicant)
                .status(VerificationStatus.AWAITING_SECONDARY_REVIEW)
                .highRisk(true)
                .riskScore(90)
                .currentReviewStage(VerificationReviewStage.SECONDARY)
                .assignedReviewer(reviewer)
                .secondaryReviewRequired(true)
                .build();

        when(verificationRepository.findById(2)).thenReturn(Optional.of(verification));
        when(verificationRiskEvaluator.calculateScore(verification)).thenReturn(88);
        when(verificationRiskEvaluator.isHighRisk(88)).thenReturn(true);
        when(verificationRepository.saveAndFlush(verification)).thenReturn(verification);
        when(decisionRepository.save(any(VerificationDecision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.findByVerification_VerificationIdAndStatus(eq(2), eq(VerificationReviewAssignmentStatus.ACTIVE)))
                .thenReturn(Optional.empty());

        VerificationReviewDecisionRequest request = VerificationReviewDecisionRequest.builder()
                .verificationId(2)
                .outcome(VerificationDecisionOutcome.APPROVED)
                .decisionReason(VerificationDecisionReason.DOCUMENT_MATCH)
                .decisionNotes("Secondary confirms")
                .build();

        VerificationDecisionResponse response = verificationReviewService.decide(REVIEWER_EMAIL, request);

        assertThat(response.getStatus()).isEqualTo(VerificationStatus.APPROVED.name());
        assertThat(response.isSecondaryReviewRequired()).isFalse();
        assertThat(response.getStage()).isEqualTo(VerificationReviewStage.SECONDARY);

        verify(verificationRepository).saveAndFlush(verification);
        verify(verificationOutcomeHandler).handleApproval(verification);
        verify(analyticsService).trackVerificationDecision(eq(verification), anyMap());

        ArgumentCaptor<VerificationAuditLog> auditCaptor = ArgumentCaptor.forClass(VerificationAuditLog.class);
        verify(auditLogRepository, atLeast(2)).save(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(VerificationAuditLog::getEventType)
                .contains(VerificationAuditEventType.SECONDARY_REVIEW_COMPLETED);
    }

    @Test
    void claim_shouldAssignReviewerAndEmitAudit() {
        Verification verification = Verification.builder()
                .verificationId(10)
                .user(applicant)
                .status(VerificationStatus.PENDING)
                .build();

        when(verificationRepository.findById(10)).thenReturn(Optional.of(verification));
        when(verificationRepository.save(verification)).thenReturn(verification);
        when(assignmentRepository.save(any(VerificationReviewAssignment.class))).thenAnswer(invocation -> {
            VerificationReviewAssignment assignment = invocation.getArgument(0);
            assignment.setAssignmentId(900L);
            return assignment;
        });

        VerificationAssignmentResponse response = verificationReviewService.claim(REVIEWER_EMAIL, 10);

        assertThat(response.getVerificationId()).isEqualTo(10);
        assertThat(verification.getStatus()).isEqualTo(VerificationStatus.IN_REVIEW);
        assertThat(verification.getAssignedReviewer()).isEqualTo(reviewer);

        verify(assignmentRepository).save(any(VerificationReviewAssignment.class));
        verify(notificationService).sendNotification(
                eq(reviewer),
                eq(NotificationType.VERIFICATION_REVIEW_ASSIGNED),
                anyString(),
                anyString(),
                anyString(),
                eq(Priority.MEDIUM),
                eq(com.mssus.app.common.enums.DeliveryMethod.IN_APP),
                eq("verification")
        );

        ArgumentCaptor<VerificationAuditLog> auditCaptor = ArgumentCaptor.forClass(VerificationAuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getEventType()).isEqualTo(VerificationAuditEventType.QUEUE_ASSIGNMENT);
    }

    @Test
    void exportAudit_shouldReturnCsvResource() throws Exception {
        Verification verification = Verification.builder()
                .verificationId(5)
                .user(applicant)
                .build();

        VerificationAuditLog auditLog = VerificationAuditLog.builder()
                .verification(verification)
                .eventType(VerificationAuditEventType.DECISION_RECORDED)
                .previousStatus("PENDING")
                .newStatus("APPROVED")
                .documentHash("hash")
                .createdAt(NOW)
                .build();

        when(auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(auditLog));

        Resource resource = verificationReviewService.exportAudit(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(resource.getFilename()).contains("verification-audit");
        String csv = new String(resource.getInputStream().readAllBytes());
        assertThat(csv).contains("verification_id,event_type,previous_status,new_status");
        assertThat(csv).contains("5,DECISION_RECORDED,PENDING,APPROVED");
    }

    private User buildUser(Integer id, String email, String name, UserType type) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        user.setFullName(name);
        user.setUserType(type);
        return user;
    }
}
