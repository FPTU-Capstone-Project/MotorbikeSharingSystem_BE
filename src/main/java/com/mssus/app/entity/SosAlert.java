package com.mssus.app.entity;

import com.mssus.app.common.enums.AlertType;
import com.mssus.app.common.enums.SosAlertStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sos_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SosAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sos_id")
    private Integer sosId;

    @ManyToOne
    @JoinColumn(name = "share_ride_id", nullable = false)
    private SharedRide sharedRide;

    @Column(name = "triggered_by", nullable = false)
    private Integer triggeredBy;

    @Column(name = "alert_type")
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "contact_info", columnDefinition = "TEXT")
    private String contactInfo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SosAlertStatus status;

    @Column(name = "acknowledged_by")
    private Integer acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
