package com.mssus.app.entity;

import com.mssus.app.common.enums.AlertType;
import com.mssus.app.common.enums.SosAlertStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sos_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SosAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sos_id")
    private Integer sosId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_ride_id")
    private SharedRide sharedRide;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by", nullable = false)
    private User triggeredBy;

    @Column(name = "alert_type")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AlertType alertType = AlertType.EMERGENCY;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "contact_info", columnDefinition = "TEXT")
    private String contactInfo;

    @Column(name = "ride_snapshot", columnDefinition = "TEXT")
    private String rideSnapshot;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SosAlertStatus status = SosAlertStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "last_escalated_at")
    private LocalDateTime lastEscalatedAt;

    @Column(name = "next_escalation_at")
    private LocalDateTime nextEscalationAt;

    @Column(name = "escalation_count")
    @Builder.Default
    private Integer escalationCount = 0;

    @Column(name = "fallback_contact_used")
    @Builder.Default
    private Boolean fallbackContactUsed = Boolean.FALSE;

    @Column(name = "auto_call_triggered")
    @Builder.Default
    private Boolean autoCallTriggered = Boolean.FALSE;

    @Column(name = "campus_security_notified")
    @Builder.Default
    private Boolean campusSecurityNotified = Boolean.FALSE;

    @Column(name = "ack_deadline")
    private LocalDateTime acknowledgementDeadline;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
