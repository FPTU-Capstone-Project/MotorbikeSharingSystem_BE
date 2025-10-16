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
    @Mapping(target = "version", source = "version")
    @Mapping(target = "base2KmVnd", expression = "java(new MoneyVnd(entity.getBase2KmVnd().longValue()))")
    @Mapping(target = "after2KmPerKmVnd", expression = "java(new MoneyVnd(entity.getAfter2KmPerKmVnd().longValue()))")
    @Mapping(target = "systemCommissionRate", source = "systemCommissionRate")
    @Mapping(target = "validFrom", source = "validFrom")
    @Mapping(target = "validUntil", source = "validUntil")
    PricingConfigDomain toDomain(PricingConfig entity);

    @InheritInverseConfiguration
    @Mapping(target = "pricingConfigId", ignore = true)
    @Mapping(target = "base2KmVnd", source = "base2KmVnd.amount")
    @Mapping(target = "after2KmPerKmVnd", source = "after2KmPerKmVnd.amount")
    @Mapping(target = "systemCommissionRate", source = "systemCommissionRate")
    @Mapping(target = "validFrom", ignore = true)   // handled by service
    @Mapping(target = "validUntil", ignore = true)  // handled by service
    PricingConfig toEntity(PricingConfigDomain domain);
}
