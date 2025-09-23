package com.mssus.app.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
@Entity
@Table(name = "user_promotions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_promotion_id")
    private Integer userPromotionId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotions promotion;

    @ManyToOne
    @JoinColumn(name = "shared_ride_request_id", nullable = false)
    private SharedRideRequest sharedRideRequest;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "discount_applied", precision = 10, scale = 2)
    private java.math.BigDecimal discountApplied;
}
