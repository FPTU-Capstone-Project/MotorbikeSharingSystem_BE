package com.mssus.app.mapper;

import com.mssus.app.entity.PricingConfig;
import com.mssus.app.pricing.config.PricingConfigDomain;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE
)
public interface PricingConfigMapper {

    @Mapping(target = "pricingConfigId", source = "pricingConfigId")
    @Mapping(target = "baseFlag", expression = "java(new MoneyVnd(entity.getBaseFlagVnd()))")
    @Mapping(target = "perKm", expression = "java(new MoneyVnd(entity.getPerKmVnd()))")
    @Mapping(target = "perMin", expression = "java(new MoneyVnd(entity.getPerMinVnd()))")
    @Mapping(target = "minFare", expression = "java(new MoneyVnd(entity.getMinFareVnd()))")
    @Mapping(target = "peakSurcharge", expression = "java(new MoneyVnd(entity.getPeakSurchargeVnd()))")
    PricingConfigDomain toDomain(PricingConfig entity);

    // Optionally map back if you ever need to persist a new config
    @InheritInverseConfiguration
    @Mapping(target = "pricingConfigId", ignore = true)
    @Mapping(target = "baseFlagVnd", source = "baseFlag.amount")
    @Mapping(target = "perKmVnd", source = "perKm.amount")
    @Mapping(target = "perMinVnd", source = "perMin.amount")
    @Mapping(target = "minFareVnd", source = "minFare.amount")
    @Mapping(target = "peakSurchargeVnd", source = "peakSurcharge.amount")
    @Mapping(target = "validFrom", ignore = true)   // handled by service
    @Mapping(target = "validUntil", ignore = true)  // handled by service
    PricingConfig toEntity(PricingConfigDomain domain);
}

