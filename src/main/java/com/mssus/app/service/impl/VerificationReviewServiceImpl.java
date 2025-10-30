package com.mssus.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.DeliveryMethod;
import com.mssus.app.common.enums.NotificationType;
import com.mssus.app.common.enums.Priority;
import com.mssus.app.common.enums.VerificationAuditEventType;
import com.mssus.app.common.enums.VerificationDecisionOutcome;
import com.mssus.app.common.enums.VerificationReviewAssignmentStatus;
import com.mssus.app.common.enums.VerificationReviewStage;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.VerificationQueueFilter;
import com.mssus.app.dto.request.VerificationReviewDecisionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VerificationAssignmentResponse;
import com.mssus.app.dto.response.VerificationDecisionResponse;
import com.mssus.app.dto.response.VerificationQueueItemResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RiderProfile;
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
import com.mssus.app.service.VerificationReviewService;
import com.mssus.app.service.domain.verification.VerificationOutcomeHandler;
import com.mssus.app.service.domain.verification.VerificationRiskEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationReviewServiceImpl implements VerificationReviewService {

        private final VerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final VerificationReviewAssignmentRepository assignmentRepository;
    private final VerificationDecisionRepository decisionRepository;
    private final VerificationAuditLogRepository auditLogRepository;
    private final VerificationOutcomeHandler verificationOutcomeHandler;
    private final VerificationRiskEvaluator verificationRiskEvaluator;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VerificationQueueItemResponse> getQueue(String reviewerEmail,
                                                                VerificationQueueFilter filter,
                                                                Pageable pageable) {
        User reviewer = requireReviewer(reviewerEmail);
        Pageable sorted = withQueueSort(pageable);
        Specification<Verification> specification = buildQueueSpecification(filter, reviewer);
        Page<Verification> page = verificationRepository.findAll(specification, sorted);
        List<VerificationQueueItemResponse> items = page.getContent().stream()
                .map(verification -> toQueueItemResponse(verification, reviewer))
                .collect(Collectors.toList());
        return buildPageResponse(page, items);
    }

    @Override
    @Transactional
    public VerificationAssignmentResponse claim(String reviewerEmail, Integer verificationId) {
        User reviewer = requireReviewer(reviewerEmail);
        Verification verification = requireVerification(verificationId);

        if (isTerminalStatus(verification.getStatus())) {
            throw ValidationException.of("Verification already resolved and cannot be claimed");
        }

        if (verification.getAssignedReviewer() != null &&
                !Objects.equals(verification.getAssignedReviewer().getUserId(), reviewer.getUserId())) {
            throw ValidationException.of("Verification already claimed by " +
                    verification.getAssignedReviewer().getFullName());
        }

        String previousStatus = verification.getStatus().name();
        LocalDateTime now = LocalDateTime.now();

        if (verification.getAssignedReviewer() == null) {
            verification.setAssignedReviewer(reviewer);
            verification.setAssignmentClaimedAt(now);
            if (!VerificationStatus.AWAITING_SECONDARY_REVIEW.equals(verification.getStatus())) {
                verification.setStatus(VerificationStatus.IN_REVIEW);
            }
            verificationRepository.save(verification);

            VerificationReviewAssignment assignment = assignmentRepository.save(
                    VerificationReviewAssignment.builder()
                            .verification(verification)
                            .reviewer(reviewer)
                            .claimedAt(now)
                            .status(VerificationReviewAssignmentStatus.ACTIVE)
                            .build()
            );

            auditLogRepository.save(buildAuditLog(verification, reviewer,
                    VerificationAuditEventType.QUEUE_ASSIGNMENT,
                    previousStatus,
                    verification.getStatus().name(),
                    Map.of("assignmentId", assignment.getAssignmentId())));

            notificationService.sendNotification(
                    reviewer,
                    NotificationType.VERIFICATION_REVIEW_ASSIGNED,
                    "Verification case assigned",
                    String.format("You claimed verification %d for %s", verification.getVerificationId(),
                            verification.getUser().getFullName()),
                    toJson(Map.of("verificationId", verification.getVerificationId())),
                    Priority.MEDIUM,
                    DeliveryMethod.IN_APP,
                    "verification"
            );
        }

        return VerificationAssignmentResponse.builder()
                .verificationId(verification.getVerificationId())
                .reviewerId(reviewer.getUserId())
                .reviewerName(reviewer.getFullName())
                .assignedAt(verification.getAssignmentClaimedAt())
                .status(verification.getStatus().name())
                .message("Verification claimed")
                .build();
    }

    @Override
    @Transactional
    public VerificationAssignmentResponse release(String reviewerEmail, Integer verificationId) {
        User reviewer = requireReviewer(reviewerEmail);
        Verification verification = requireVerification(verificationId);

        if (verification.getAssignedReviewer() == null ||
                !Objects.equals(verification.getAssignedReviewer().getUserId(), reviewer.getUserId())) {
            throw ValidationException.of("Verification is not currently assigned to this reviewer");
        }

        String previousStatus = verification.getStatus().name();
        LocalDateTime now = LocalDateTime.now();
        VerificationReviewAssignment assignment = assignmentRepository
                .findByVerification_VerificationIdAndStatus(verificationId, VerificationReviewAssignmentStatus.ACTIVE)
                .orElse(null);
        if (assignment != null) {
            assignment.setStatus(VerificationReviewAssignmentStatus.RELEASED);
            assignment.setReleasedAt(now);
            assignmentRepository.save(assignment);
        }

        verification.setAssignedReviewer(null);
        verification.setAssignmentClaimedAt(null);
        if (!VerificationStatus.AWAITING_SECONDARY_REVIEW.equals(verification.getStatus())) {
            verification.setStatus(VerificationStatus.PENDING);
        }
        verificationRepository.save(verification);

        auditLogRepository.save(buildAuditLog(verification, reviewer,
                VerificationAuditEventType.STATUS_CHANGED,
                previousStatus,
                verification.getStatus().name(),
                Collections.singletonMap("action", "released")));

        return VerificationAssignmentResponse.builder()
                .verificationId(verification.getVerificationId())
                .reviewerId(reviewer.getUserId())
                .reviewerName(reviewer.getFullName())
                .assignedAt(null)
                .status(verification.getStatus().name())
                .message("Verification released back to queue")
                .build();
    }

    @Override
    @Transactional
    public VerificationDecisionResponse decide(String reviewerEmail, VerificationReviewDecisionRequest request) {
        User reviewer = requireReviewer(reviewerEmail);
        Verification verification = requireVerification(request.getVerificationId());

        verifyDecisionRequest(request);
        VerificationReviewStage stage = resolveStage(verification, request.isOverrideSecondaryRequirement());

        ensureReviewerHasOwnership(reviewer, verification, stage);

        LocalDateTime now = LocalDateTime.now();
        updateRiskMetrics(verification);

        VerificationDecision decision = VerificationDecision.builder()
                .verification(verification)
                .reviewer(reviewer)
                .reviewStage(stage)
                .outcome(request.getOutcome())
                .decisionReason(request.getDecisionReason())
                .decisionNotes(request.getDecisionNotes())
                .evidenceReferences(toJson(request.getEvidenceReferences()))
                .documentAnnotations(toJson(request.getAnnotations()))
                .overrideJustification(request.isOverrideSecondaryRequirement() ? request.getOverrideJustification() : null)
                .build();
        decisionRepository.save(decision);

        VerificationStatus previousStatus = verification.getStatus();

        boolean shouldHandleApproval = false;

        if (request.getOutcome() == VerificationDecisionOutcome.REJECTED) {
            verification.setStatus(VerificationStatus.REJECTED);
            verification.setVerifiedBy(reviewer);
            verification.setVerifiedAt(now);
            verification.setCurrentReviewStage(stage);
            verification.setSecondaryReviewRequired(false);
            verification.setAssignedReviewer(null);
            verification.setAssignmentClaimedAt(null);
            verificationOutcomeHandler.handleRejection(verification);
        } else {
            shouldHandleApproval = handleApprovalFlow(reviewer, verification, previousStatus, stage, request, now);
        }

        if (shouldHandleApproval) {
            verificationRepository.saveAndFlush(verification);
            verificationOutcomeHandler.handleApproval(verification);
        } else {
            verificationRepository.save(verification);
        }
        completeActiveAssignment(verification.getVerificationId(), now);

        auditLogRepository.save(buildAuditLog(verification, reviewer,
                VerificationAuditEventType.DECISION_RECORDED,
                previousStatus.name(),
                verification.getStatus().name(),
                Map.of(
                        "stage", stage.name(),
                        "outcome", request.getOutcome().name(),
                        "reason", request.getDecisionReason().name()
                )));

        analyticsService.trackVerificationDecision(verification, Map.of(
                "stage", stage.name(),
                "outcome", request.getOutcome().name(),
                "highRisk", verification.getHighRisk()
        ));

        return VerificationDecisionResponse.builder()
                .verificationId(verification.getVerificationId())
                .status(verification.getStatus().name())
                .stage(verification.getCurrentReviewStage())
                .secondaryReviewRequired(Boolean.TRUE.equals(verification.getSecondaryReviewRequired()))
                .outcome(request.getOutcome())
                .decidedAt(now)
                .message("Decision recorded")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource exportAudit(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ValidationException.of("Both from and to dates are required for audit export");
        }
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.atTime(LocalTime.MAX);

        List<VerificationAuditLog> logs = auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(fromTs, toTs);
        StringBuilder builder = new StringBuilder();
        builder.append("verification_id,event_type,previous_status,new_status,reviewer_id,created_at,document_hash,payload\n");
        logs.forEach(log -> builder.append(csvLine(log)).append('\n'));

        byte[] bytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        String filename = String.format("verification-audit-%s-%s.csv", from, to);
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    private boolean handleApprovalFlow(User reviewer,
                                    Verification verification,
                                    VerificationStatus previousStatus,
                                    VerificationReviewStage stage,
                                    VerificationReviewDecisionRequest request,
                                    LocalDateTime now) {
        if (stage == VerificationReviewStage.PRIMARY &&
                verification.getHighRisk() &&
                !request.isOverrideSecondaryRequirement()) {
            verification.setStatus(VerificationStatus.AWAITING_SECONDARY_REVIEW);
            verification.setCurrentReviewStage(VerificationReviewStage.SECONDARY);
            verification.setSecondaryReviewRequired(true);
            verification.setAssignedReviewer(null);
            verification.setAssignmentClaimedAt(null);

            auditLogRepository.save(buildAuditLog(verification, reviewer,
                    VerificationAuditEventType.SECONDARY_REVIEW_TRIGGERED,
                    previousStatus.name(),
                    verification.getStatus().name(),
                    Map.of("reason", request.getDecisionReason().name())));

            analyticsService.trackVerificationEscalation(verification, Map.of(
                    "trigger", "high_risk_primary_approval",
                    "reviewer", reviewer.getUserId()
            ));

            notifySecondaryReviewers(verification);
            return false;
        } else {
            verification.setStatus(VerificationStatus.APPROVED);
            verification.setVerifiedBy(reviewer);
            verification.setVerifiedAt(now);
            verification.setSecondaryReviewRequired(false);
            verification.setCurrentReviewStage(stage);
            verification.setAssignedReviewer(null);
            verification.setAssignmentClaimedAt(null);

            if (stage == VerificationReviewStage.SECONDARY) {
                auditLogRepository.save(buildAuditLog(verification, reviewer,
                        VerificationAuditEventType.SECONDARY_REVIEW_COMPLETED,
                        previousStatus.name(),
                        verification.getStatus().name(),
                        Map.of("stage", stage.name())));
            } else if (stage == VerificationReviewStage.OVERRIDE) {
                auditLogRepository.save(buildAuditLog(verification, reviewer,
                        VerificationAuditEventType.OVERRIDE_APPLIED,
                        previousStatus.name(),
                        verification.getStatus().name(),
                        Map.of(
                                "stage", stage.name(),
                                "overrideJustification", request.getOverrideJustification()
                        )));
            }
            return true;
        }
    }

    private void notifySecondaryReviewers(Verification verification) {
        List<User> admins = userRepository.findByUserType(com.mssus.app.common.enums.UserType.ADMIN);
        if (admins.isEmpty()) {
            return;
        }
        String title = "High-risk verification requires secondary review";
        String message = String.format("Verification %d for %s has been escalated",
                verification.getVerificationId(),
                verification.getUser().getFullName());
        String payload = toJson(Map.of(
                "verificationId", verification.getVerificationId(),
                "userId", verification.getUser().getUserId(),
                "riskScore", verification.getRiskScore()
        ));
        admins.forEach(admin -> notificationService.sendNotification(
                admin,
                NotificationType.VERIFICATION_ESCALATED,
                title,
                message,
                payload,
                Priority.HIGH,
                DeliveryMethod.IN_APP,
                "verification"
        ));

        auditLogRepository.save(buildAuditLog(
                verification,
                null,
                VerificationAuditEventType.ESCALATION_NOTIFIED,
                verification.getStatus().name(),
                verification.getStatus().name(),
                Map.of("recipientCount", admins.size())
        ));
    }

    private void completeActiveAssignment(Integer verificationId, LocalDateTime completedAt) {
        assignmentRepository.findByVerification_VerificationIdAndStatus(verificationId, VerificationReviewAssignmentStatus.ACTIVE)
                .ifPresent(assignment -> {
                    assignment.setStatus(VerificationReviewAssignmentStatus.COMPLETED);
                    assignment.setReleasedAt(completedAt);
                    assignmentRepository.save(assignment);
                });
    }

    private void ensureReviewerHasOwnership(User reviewer, Verification verification, VerificationReviewStage stage) {
        if (stage == VerificationReviewStage.PRIMARY || stage == VerificationReviewStage.SECONDARY) {
            if (verification.getAssignedReviewer() == null ||
                    !Objects.equals(verification.getAssignedReviewer().getUserId(), reviewer.getUserId())) {
                throw ValidationException.of("Reviewer must claim the verification before recording a decision");
            }
        }
    }

    private void verifyDecisionRequest(VerificationReviewDecisionRequest request) {
        if (request.getOutcome() == VerificationDecisionOutcome.REJECTED &&
                request.getDecisionReason() == null) {
            throw ValidationException.of("Rejection requires an explicit decision reason");
        }
        if (request.isOverrideSecondaryRequirement() &&
                (request.getOverrideJustification() == null || request.getOverrideJustification().isBlank())) {
            throw ValidationException.of("Override justification is required when bypassing secondary review");
        }
    }

    private VerificationReviewStage resolveStage(Verification verification, boolean override) {
        if (override) {
            return VerificationReviewStage.OVERRIDE;
        }
        if (VerificationStatus.AWAITING_SECONDARY_REVIEW.equals(verification.getStatus())) {
            return VerificationReviewStage.SECONDARY;
        }
        return VerificationReviewStage.PRIMARY;
    }

    private void updateRiskMetrics(Verification verification) {
        int score = verificationRiskEvaluator.calculateScore(verification);
        verification.setRiskScore(score);
        boolean highRisk = verificationRiskEvaluator.isHighRisk(score);
        verification.setHighRisk(highRisk);
        if (!highRisk) {
            verification.setSecondaryReviewRequired(false);
        }
    }

    private Specification<Verification> buildQueueSpecification(VerificationQueueFilter filter, User reviewer) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();

            VerificationStatus targetStatus = null;
            if (filter != null && filter.getStatus() != null && !filter.getStatus().isBlank()) {
                try {
                    targetStatus = VerificationStatus.valueOf(filter.getStatus().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw ValidationException.of("Unknown verification status: " + filter.getStatus());
                }
            }

            if (targetStatus != null) {
                predicates.add(cb.equal(root.get("status"), targetStatus));
            } else {
                predicates.add(root.get("status").in(List.of(
                        VerificationStatus.PENDING,
                        VerificationStatus.IN_REVIEW,
                        VerificationStatus.AWAITING_SECONDARY_REVIEW
                )));
            }

            if (filter != null && Boolean.TRUE.equals(filter.getHighRiskOnly())) {
                predicates.add(cb.isTrue(root.get("highRisk")));
            }

            Join<Verification, User> userJoin = root.join("user");
            Join<User, DriverProfile> driverJoin = userJoin.join("driverProfile", jakarta.persistence.criteria.JoinType.LEFT);
            Join<User, RiderProfile> riderJoin = userJoin.join("riderProfile", jakarta.persistence.criteria.JoinType.LEFT);

            if (filter != null && filter.getProfileType() != null && !filter.getProfileType().isBlank()) {
                String profile = filter.getProfileType().toUpperCase(Locale.ROOT);
                if ("DRIVER".equals(profile)) {
                    predicates.add(cb.isNotNull(driverJoin.get("driverId")));
                } else if ("RIDER".equals(profile)) {
                    predicates.add(cb.isNotNull(riderJoin.get("riderId")));
                } else {
                    throw ValidationException.of("Unknown profile type filter: " + filter.getProfileType());
                }
            }

            if (filter != null && filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(userJoin.get("fullName")), pattern),
                        cb.like(cb.lower(userJoin.get("email")), pattern)
                ));
            }

            if (filter != null && Boolean.TRUE.equals(filter.getOnlyMine())) {
                predicates.add(cb.equal(root.get("assignedReviewer"), reviewer));
            } else {
                predicates.add(cb.or(
                        cb.isNull(root.get("assignedReviewer")),
                        cb.equal(root.get("assignedReviewer"), reviewer)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private VerificationQueueItemResponse toQueueItemResponse(Verification verification, User reviewer) {
        User subject = verification.getUser();
        User assignee = verification.getAssignedReviewer();
        return VerificationQueueItemResponse.builder()
                .verificationId(verification.getVerificationId())
                .userId(subject.getUserId())
                .userName(subject.getFullName())
                .profileType(resolveProfileType(subject))
                .type(verification.getType().name())
                .status(verification.getStatus().name())
                .riskScore(Optional.ofNullable(verification.getRiskScore()).orElse(0))
                .highRisk(Boolean.TRUE.equals(verification.getHighRisk()))
                .submittedAt(verification.getCreatedAt())
                .assignmentClaimedAt(verification.getAssignmentClaimedAt())
                .assignedReviewerId(assignee != null ? assignee.getUserId() : null)
                .assignedReviewerName(assignee != null ? assignee.getFullName() : null)
                .claimable(assignee == null || Objects.equals(assignee.getUserId(), reviewer.getUserId()))
                .currentStage(Optional.ofNullable(verification.getCurrentReviewStage()).orElse(VerificationReviewStage.PRIMARY))
                .secondaryReviewRequired(Boolean.TRUE.equals(verification.getSecondaryReviewRequired()))
                .build();
    }

    private PageResponse<VerificationQueueItemResponse> buildPageResponse(Page<Verification> page,
                                                                          List<VerificationQueueItemResponse> content) {
        return PageResponse.<VerificationQueueItemResponse>builder()
                .data(content)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(page.getNumber() + 1)
                        .pageSize(page.getSize())
                        .totalPages(page.getTotalPages())
                        .totalRecords(page.getTotalElements())
                        .build())
                .build();
    }

    private Pageable withQueueSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.desc("highRisk"), Sort.Order.desc("riskScore"), Sort.Order.asc("createdAt")));
    }

    private User requireReviewer(String email) {
        return userRepository.findByEmailWithProfiles(email)
                .orElseThrow(() -> NotFoundException.userNotFound(email));
    }

    private Verification requireVerification(Integer verificationId) {
        return verificationRepository.findById(verificationId)
                .orElseThrow(() -> NotFoundException.verificationNotFound(verificationId));
    }

    private boolean isTerminalStatus(VerificationStatus status) {
        return status == VerificationStatus.APPROVED || status == VerificationStatus.REJECTED || status == VerificationStatus.EXPIRED;
    }

    private VerificationAuditLog buildAuditLog(Verification verification,
                                               User actor,
                                               VerificationAuditEventType eventType,
                                               String previousStatus,
                                               String newStatus,
                                               Map<String, Object> payload) {
        return VerificationAuditLog.builder()
                .verification(verification)
                .performedBy(actor)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .payload(toJson(payload))
                .documentHash(computeDocumentHash(verification))
                .build();
    }

    private String computeDocumentHash(Verification verification) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = String.join("|",
                    Optional.ofNullable(verification.getDocumentUrl()).orElse(""),
                    Optional.ofNullable(verification.getMetadata()).orElse(""));
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute document hash", e);
        }
    }

    private String csvLine(VerificationAuditLog log) {
        return String.join(",",
                value(log.getVerification().getVerificationId()),
                value(log.getEventType().name()),
                value(log.getPreviousStatus()),
                value(log.getNewStatus()),
                value(log.getPerformedBy() != null ? log.getPerformedBy().getUserId() : null),
                value(log.getCreatedAt()),
                value(log.getDocumentHash()),
                quote(Optional.ofNullable(log.getPayload()).orElse(""))
        );
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String resolveProfileType(User user) {
        if (user.getDriverProfile() != null) {
            return "DRIVER";
        }
        if (user.getRiderProfile() != null) {
            return "RIDER";
        }
        return "USER";
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise payload", e);
        }
    }
}

