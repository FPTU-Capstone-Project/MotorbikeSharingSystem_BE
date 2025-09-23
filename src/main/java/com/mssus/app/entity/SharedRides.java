package com.mssus.app.entity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shared_rides")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedRides {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shared_ride_id")
    private Integer sharedRideId;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverProfile driver;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "start_location_id", nullable = false)
    private Integer startLocationId;

    @Column(name = "end_location_id", nullable = false)
    private Integer endLocationId;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "max_passengers")
    private Integer maxPassengers;

    @Column(name = "current_passengers")
    private Integer currentPassengers;

    @Column(name = "base_fare", precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "per_km_rate", precision = 10, scale = 2)
    private BigDecimal perKmRate;

    @Column(name = "estimated_duration")
    private Integer estimatedDuration;

    @Column(name = "estimated_distance")
    private Float estimatedDistance;

    @Column(name = "actual_duration")
    private Integer actualDuration;

    @Column(name = "actual_distance")
    private Float actualDistance;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum RideStatus {
        SCHEDULED,
        ACTIVE,
        COMPLETED,
        CANCELLED,
        PENDING
    }
}
