package com.mssus.app.entity;

import com.mssus.app.common.enums.PaymentMethod;
import com.mssus.app.common.enums.RiderProfileStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rider_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RiderProfile {

    @Id
    @Column(name = "rider_id")
    private Integer riderId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "rider_id", referencedColumnName = "user_id")
    private User user;

    @Column(name = "emergency_contact")
    private String emergencyContact;

    @Column(name = "total_rides")
    @Builder.Default
    private Integer totalRides = 0;

    @Column(name = "total_spent", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RiderProfileStatus status = RiderProfileStatus.ACTIVE;

    @Column(name = "preferred_payment_method", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentMethod preferredPaymentMethod = PaymentMethod.WALLET;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
