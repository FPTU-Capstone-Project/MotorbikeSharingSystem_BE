package com.mssus.app.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
@Entity
@Table(name = "ai_matching_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMatchingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Integer logId;

    @ManyToOne
    @JoinColumn(name = "shared_ride_request_id", nullable = false)
    private SharedRideRequest sharedRideRequest;

    @Column(name = "algorithm_version")
    private String algorithmVersion;

    @Column(name = "request_location", columnDefinition = "TEXT")
    private String requestLocation;

    @Column(name = "search_radius_km")
    private Float searchRadiusKm;

    @Column(name = "available_drivers_count")
    private Integer availableDriversCount;

    @Column(name = "matching_factors", columnDefinition = "TEXT")
    private String matchingFactors;

    @Column(name = "potential_matches", columnDefinition = "TEXT")
    private String potentialMatches;

    @ManyToOne
    @JoinColumn(name = "selected_driver_id", nullable = false)
    private DriverProfile selectedDriver;

    @Column(name = "matching_score")
    private Float matchingScore;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
