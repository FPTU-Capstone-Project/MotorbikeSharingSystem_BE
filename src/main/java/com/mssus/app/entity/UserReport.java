package com.mssus.app.entity;

import com.mssus.app.common.enums.ReportPriority;
import com.mssus.app.common.enums.ReportStatus;
import com.mssus.app.common.enums.ReportType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_reports", indexes = {
    @Index(name = "idx_user_reports_status", columnList = "status"),
    @Index(name = "idx_user_reports_type", columnList = "report_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Integer reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolver_id")
    private User resolver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_ride_id")
    private SharedRide sharedRide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverProfile driver;

    @Column(name = "report_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "resolution_message", columnDefinition = "TEXT")
    private String resolutionMessage;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "priority", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReportPriority priority = ReportPriority.MEDIUM;

    @Column(name = "driver_response", columnDefinition = "TEXT")
    private String driverResponse;

    @Column(name = "driver_responded_at")
    private LocalDateTime driverRespondedAt;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "reporter_chat_started_at")
    private LocalDateTime reporterChatStartedAt;

    @Column(name = "reporter_last_reply_at")
    private LocalDateTime reporterLastReplyAt;

    @Column(name = "reported_chat_started_at")
    private LocalDateTime reportedChatStartedAt;

    @Column(name = "reported_last_reply_at")
    private LocalDateTime reportedLastReplyAt;

    @Column(name = "auto_closed_at")
    private LocalDateTime autoClosedAt;

    @Column(name = "auto_closed_reason", length = 100)
    private String autoClosedReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
