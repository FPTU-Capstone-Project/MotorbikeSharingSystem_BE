package com.mssus.app.entity;

import com.mssus.app.common.enums.SharedRideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "shared_rides")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedRide {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shared_ride_id")
    private Integer sharedRideId;

    @OneToMany(mappedBy = "sharedRide", fetch = FetchType.LAZY)
    private List<SharedRideRequest> rideRequests;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverProfile driver;

    @ManyToOne
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne
    @JoinColumn(name = "start_location_id", nullable = false)
    private Location startLocation;

    @ManyToOne
    @JoinColumn(name = "end_location_id", nullable = false)
    private Location endLocation;

//    @Column(name = "start_location_id", nullable = false)
//    private Integer startLocationId;
//
//    @Column(name = "end_location_id", nullable = false)
//    private Integer endLocationId;

//    @Column(name = "start_lat", nullable = false)
//    private double startLat;
//
//    @Column(name = "start_lng", nullable = false)
//    private double startLng;
//
//    @Column(name = "end_lat", nullable = false)
//    private double endLat;
//
//    @Column(name = "end_lng", nullable = false)
//    private double endLng;

    @Column(name = "status", length = 50)
    @Enumerated(EnumType.STRING)
    private SharedRideStatus status;

    @Column(name = "max_passengers")
    private Integer maxPassengers;

    @Column(name = "current_passengers")
    private Integer currentPassengers;

    @ManyToOne
    @JoinColumn(name = "pricing_config_id", nullable = false)
    private PricingConfig pricingConfig;

    @Column(name = "driver_earned_amount", precision = 19, scale = 2)
    private BigDecimal driverEarnedAmount;

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
}
