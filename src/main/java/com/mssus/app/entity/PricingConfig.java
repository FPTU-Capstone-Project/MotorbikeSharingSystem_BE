package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private Instant version;

    @Column(name = "base_2km_vnd", nullable = false, precision = 18, scale = 2)
    private BigDecimal base2KmVnd;

    @Column(name = "after_2Km_per_km_vnd", nullable = false, precision = 18, scale = 2)
    private BigDecimal after2KmPerKmVnd;

    @Column(name = "system_commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal systemCommissionRate;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;
}
