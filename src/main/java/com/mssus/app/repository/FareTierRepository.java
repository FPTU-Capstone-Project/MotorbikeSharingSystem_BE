package com.mssus.app.repository;

import com.mssus.app.entity.FareTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FareTierRepository extends JpaRepository<FareTier, Integer> {
    List<FareTier> findByPricingConfig_PricingConfigId(Integer pricingConfigId);
}
