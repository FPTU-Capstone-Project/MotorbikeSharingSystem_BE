package com.mssus.app.entity;

import com.mssus.app.common.enums.PricingConfigStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    @OneToMany(mappedBy = "pricingConfig", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<FareTier> fareTiers;

//    @Column(name = "base_2km_vnd", nullable = false, precision = 18, scale = 2)
//    private BigDecimal base2KmVnd;
//
//    @Column(name = "after_2Km_per_km_vnd", nullable = false, precision = 18, scale = 2)
//    private BigDecimal after2KmPerKmVnd;

    @Column(name = "system_commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal systemCommissionRate;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PricingConfigStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "change_reason")
    private String changeReason;

    @Column(name = "notice_sent_at")
    private Instant noticeSentAt;
}
