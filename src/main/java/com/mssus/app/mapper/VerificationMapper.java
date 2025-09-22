package com.mssus.app.mapper;

import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.entity.Verification;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface VerificationMapper {

    VerificationResponse toResponse(Verification entity);
}
