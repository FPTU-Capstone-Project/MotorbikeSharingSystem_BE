package com.mssus.app.mapper;

import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.entity.AdminProfile;
import com.mssus.app.entity.Verification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface VerificationMapper {

    @Mapping(target = "verifiedBy", expression = "java(map(entity.getVerifiedBy()))")
    VerificationResponse toResponse(Verification entity);

    default String map(AdminProfile value) {
        return String.valueOf(1); //TODO: change this to actual value later
    }
}
