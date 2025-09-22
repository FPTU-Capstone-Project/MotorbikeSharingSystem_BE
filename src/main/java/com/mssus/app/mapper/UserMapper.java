package com.mssus.app.mapper;

import com.mssus.app.dto.request.CreateAccountRequest;
import com.mssus.app.dto.request.RegisterRequest;
import com.mssus.app.dto.request.UpdateAccountRequest;
import com.mssus.app.dto.response.RegisterResponse;
import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.entity.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    uses = {ProfileMapper.class}
)
public interface UserMapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userType", constant = "student")
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "phoneVerified", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "riderProfile", ignore = true)
    @Mapping(target = "driverProfile", ignore = true)
    @Mapping(target = "adminProfile", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "profilePhotoUrl", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    Users toEntity(RegisterRequest request);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "phoneVerified", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "riderProfile", ignore = true)
    @Mapping(target = "driverProfile", ignore = true)
    @Mapping(target = "adminProfile", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "profilePhotoUrl", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    Users toEntity(CreateAccountRequest request);

    @Mapping(target = "userType", source = "primaryRole")
    RegisterResponse toRegisterResponse(Users user);

    @Mapping(target = "user", source = ".")
    @Mapping(target = "riderProfile", source = "riderProfile")
    @Mapping(target = "driverProfile", source = "driverProfile")
    @Mapping(target = "adminProfile", source = "adminProfile")
    @Mapping(target = "wallet", source = "wallet")
    UserProfileResponse toProfileResponse(Users user);

    @Mapping(target = "userType", source = "primaryRole")
    UserProfileResponse.UserInfo toUserInfo(Users user);

    void updateUserFromRequest(UpdateAccountRequest request, @MappingTarget Users user);
}
