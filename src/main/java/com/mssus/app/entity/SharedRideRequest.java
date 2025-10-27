package com.mssus.app.entity;

import com.mssus.app.common.enums.RequestKind;
import com.mssus.app.common.enums.SharedRideRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shared_ride_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Builder
public class SharedRideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shared_ride_request_id")
    private Integer sharedRideRequestId;

    @ManyToOne
    @JoinColumn(name = "shared_ride_id", nullable = true)
    private SharedRide sharedRide;

    @Column(name = "request_kind", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private RequestKind requestKind;

    @ManyToOne
    @JoinColumn(name = "rider_id", nullable = false)
    private RiderProfile rider;

    @Column(name = "distance_meters", nullable = false)
    private Integer distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private Long durationSeconds;

    @ManyToOne
    @JoinColumn(name = "pickup_location_id", nullable = false)
    private Location pickupLocation;

    @ManyToOne
    @JoinColumn(name = "dropoff_location_id", nullable = false)
    private Location dropoffLocation;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private SharedRideRequestStatus status;

    @ManyToOne
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @ManyToOne
    @JoinColumn(name = "pricing_config_id", nullable = false)
    private PricingConfig pricingConfig;

    @Column(name = "subtotal_fare", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalFare;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_fare", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalFare;

    @Column(name = "pickup_time", nullable = false)
    private LocalDateTime pickupTime;

    @Column(name = "special_requests", columnDefinition = "TEXT")
    private String specialRequests;

    @Column(name = "estimated_pickup_time")
    private LocalDateTime estimatedPickupTime;

    @Column(name = "actual_pickup_time")
    private LocalDateTime actualPickupTime;

    @Column(name = "estimated_dropoff_time")
    private LocalDateTime estimatedDropoffTime;

    @Column(name = "actual_dropoff_time")
    private LocalDateTime actualDropoffTime;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "polyline", columnDefinition = "TEXT")
    private String polyline;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
