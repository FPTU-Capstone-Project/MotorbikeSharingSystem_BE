package com.mssus.app.repository;

import com.mssus.app.common.enums.PricingConfigStatus;
import com.mssus.app.entity.PricingConfig;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Optional;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, Integer> {
  @Query("""
         SELECT pc
         FROM PricingConfig pc
         WHERE pc.status IN ('ACTIVE', 'SCHEDULED')
           AND pc.validFrom IS NOT NULL
           AND :now >= pc.validFrom
           AND (pc.validUntil IS NULL OR :now < pc.validUntil)
         ORDER BY pc.validFrom DESC
         LIMIT 1
      """)
  Optional<PricingConfig> findActive(@Param("now") Instant now);

  Optional<PricingConfig> findFirstByStatus(PricingConfigStatus status);

  Page<PricingConfig> findByStatus(PricingConfigStatus status, Pageable pageable);

  @Query("""
         SELECT pc
         FROM PricingConfig pc
         WHERE pc.status = 'SCHEDULED'
         ORDER BY pc.validFrom ASC
         LIMIT 1
      """)
  Optional<PricingConfig> findScheduled();

  Optional<PricingConfig> findByVersion(Instant version);

  PricingConfig findByPricingConfigId(Integer pricingConfigId);
}
