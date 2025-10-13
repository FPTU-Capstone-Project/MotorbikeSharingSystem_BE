package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "pricing_configs")
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pricing_config_id", nullable = false)
    private Integer pricingConfigId;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "base_flag_vnd", nullable = false)
    private Long baseFlagVnd;

    @Column(name = "per_km_vnd", nullable = false)
    private Long perKmVnd;

    @Column(name = "per_min_vnd", nullable = false)
    private Long perMinVnd;

    @Column(name = "min_fare_vnd", nullable = false)
    private Long minFareVnd;

    @Column(name = "peak_surcharge_vnd", nullable = false)
    private Long peakSurchargeVnd;

    @Column(name = "default_commission", nullable = false, precision = 5, scale = 4)
    private BigDecimal defaultCommission;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;
}
