package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "fare_tiers")
@NoArgsConstructor
@AllArgsConstructor
public class FareTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fare_tier_id", nullable = false)
    private Integer fareTierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_config_id", nullable = false)
    private PricingConfig pricingConfig;

    @Column(name = "tier_level", nullable = false, length = 100)
    private Integer tierLevel;

    @Column(name = "description")
    private String description;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "min_km", nullable = false)
    private Integer minKm;

    @Column(name = "max_km", nullable = false)
    private Integer maxKm;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
