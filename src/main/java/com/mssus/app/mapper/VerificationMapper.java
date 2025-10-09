package com.mssus.app.mapper;

import com.mssus.app.dto.response.StudentVerificationResponse;
import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.entity.Verification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface VerificationMapper {

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "verifiedBy.userId", target = "verifiedBy")
    VerificationResponse mapToVerificationResponse(Verification verification);

    @Mapping(source = "user.userId", target = "userId")
    @Mapping(source = "user.fullName", target = "fullName")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.phone", target = "phone")
    @Mapping(source = "user.studentId", target = "studentId")
    @Mapping(source = "verifiedBy.userId", target = "verifiedBy")
    StudentVerificationResponse mapToStudentVerificationResponse(Verification verification);
}
