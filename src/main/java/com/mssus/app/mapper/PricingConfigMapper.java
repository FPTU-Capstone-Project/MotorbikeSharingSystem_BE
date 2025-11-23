package com.mssus.app.mapper;

import com.mssus.app.entity.PricingConfig;
import com.mssus.app.service.domain.pricing.config.PricingConfigDomain;
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
    @Mapping(target = "version", source = "version")
    @Mapping(target = "systemCommissionRate", source = "systemCommissionRate")
    @Mapping(target = "validFrom", source = "validFrom")
    @Mapping(target = "validUntil", source = "validUntil")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "fareTiers", ignore = true)          // handled separately
    PricingConfigDomain toDomain(PricingConfig entity);

    @InheritInverseConfiguration
    @Mapping(target = "pricingConfigId", ignore = true)
    @Mapping(target = "systemCommissionRate", source = "systemCommissionRate")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "fareTiers", ignore = true)          // handled separately
    @Mapping(target = "validFrom", ignore = true)   // handled by service
    @Mapping(target = "validUntil", ignore = true)  // handled by service
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "changeReason", ignore = true)
    @Mapping(target = "noticeSentAt", ignore = true)
    PricingConfig toEntity(PricingConfigDomain domain);
}
