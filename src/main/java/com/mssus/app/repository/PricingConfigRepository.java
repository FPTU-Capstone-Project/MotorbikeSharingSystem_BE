package com.mssus.app.repository;

import com.mssus.app.entity.PricingConfig;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface PricingConfigRepository extends JpaRepository<PricingConfig, Integer> {
  @Query("""
         SELECT pc
         FROM PricingConfig pc
         WHERE :now >= pc.validFrom
           AND (pc.validUntil IS NULL OR :now < pc.validUntil)
         ORDER BY pc.validFrom DESC
         LIMIT 1
      """)
  Optional<PricingConfig> findActive(@Param("now") Instant now);

  Optional<PricingConfig> findByVersion(Instant version);

  PricingConfig findByPricingConfigId(Integer pricingConfigId);
}
