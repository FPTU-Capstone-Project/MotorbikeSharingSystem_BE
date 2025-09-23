package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
public class SharedRideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shared_ride_request_id")
    private Integer sharedRideRequestId;

    @ManyToOne
    @JoinColumn(name = "share_ride_id", nullable = false)
    private SharedRides sharedRide;

    @ManyToOne
    @JoinColumn(name = "rider_id", nullable = false)
    private RiderProfile rider;

    @Column(name = "pickup_location_id")
    private Integer pickupLocationId;

    @Column(name = "dropoff_location_id")
    private Integer dropoffLocationId;

    @Column(name = "status")
    private String status;

    @Column(name = "fare_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal fareAmount;

    @Column(name = "original_fare", precision = 19, scale = 2)
    private BigDecimal originalFare;

    @Column(name = "discount_amount", precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "pickup_time", nullable = false)
    private LocalDateTime pickupTime;

    @Column(name = "max_wait_time")
    private Integer maxWaitTime;

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

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
