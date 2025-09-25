package com.mssus.app.entity;

import com.mssus.app.common.enums.DriverProfileStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_profiles", indexes = {
    @Index(name = "idx_license_number", columnList = "license_number"),
    @Index(name = "idx_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DriverProfile {

    @Id
    @Column(name = "driver_id")
    private Integer driverId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "driver_id", referencedColumnName = "user_id")
    private User user;

    @Column(name = "license_number", unique = true, nullable = false)
    private String licenseNumber;

    @Column(name = "license_verified_at")
    private LocalDateTime licenseVerifiedAt;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DriverProfileStatus status = DriverProfileStatus.PENDING;

    @Column(name = "rating_avg")
    @Builder.Default
    private Float ratingAvg = 5.0f;

    @Column(name = "total_shared_rides")
    @Builder.Default
    private Integer totalSharedRides = 0;

    @Column(name = "total_earned", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalEarned = BigDecimal.ZERO;

    @Column(name = "commission_rate", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal commissionRate = new BigDecimal("0.15");

    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = false;

    @Column(name = "max_passengers")
    @Builder.Default
    private Integer maxPassengers = 1;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
