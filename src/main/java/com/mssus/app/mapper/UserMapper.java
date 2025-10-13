package com.mssus.app.mapper;

import com.mssus.app.common.enums.UserType;
import com.mssus.app.dto.request.CreateAccountRequest;
import com.mssus.app.dto.request.RegisterRequest;
import com.mssus.app.dto.request.UpdateAccountRequest;
import com.mssus.app.dto.response.RegisterResponse;
import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.entity.User;
import com.mssus.app.common.enums.UserStatus;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    uses = {ProfileMapper.class}
)
public interface UserMapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userType", expression = "java(toUserType(request.getRole()))")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "phoneVerified", constant = "false")
    @Mapping(target = "tokenVersion", constant = "0")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "riderProfile", ignore = true)
    @Mapping(target = "driverProfile", ignore = true)
//    @Mapping(target = "adminProfile", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "profilePhotoUrl", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    User toEntity(RegisterRequest request);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "userType", expression = "java(toUserType(request.getUserType() != null ? request.getUserType() : \"USER\"))")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "phoneVerified", constant = "false")
    @Mapping(target = "tokenVersion", constant = "0")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "riderProfile", ignore = true)
    @Mapping(target = "driverProfile", ignore = true)
//    @Mapping(target = "adminProfile", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "profilePhotoUrl", ignore = true)
    @Mapping(target = "studentId", ignore = true)
    User toEntity(CreateAccountRequest request);

    @Mapping(target = "userType", expression = "java(toReadableType(user.getUserType()))")
    RegisterResponse toRegisterResponse(User user);

    @Mapping(target = "user", source = ".")
    @Mapping(target = "riderProfile", source = "riderProfile")
    @Mapping(target = "driverProfile", ignore = true)
    @Mapping(target = "wallet", source = "wallet")
    @Mapping(target = "availableProfiles", expression = "java(getUserProfiles(user))")
    @Mapping(target = "activeProfile", ignore = true)
    UserProfileResponse toRiderProfileResponse(User user);

    @Mapping(target = "user", source = ".")
    @Mapping(target = "riderProfile", ignore = true)
    @Mapping(target = "driverProfile", source = "driverProfile")
    @Mapping(target = "wallet", source = "wallet")
    @Mapping(target = "availableProfiles", expression = "java(getUserProfiles(user))")
    @Mapping(target = "activeProfile", ignore = true)
    UserProfileResponse toDriverProfileResponse(User user);

    @Mapping(target = "user", source = ".")
    @Mapping(target = "riderProfile", ignore = true)
    @Mapping(target = "driverProfile", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "availableProfiles", ignore = true)
    @Mapping(target = "activeProfile", ignore = true)
    UserProfileResponse toAdminProfileResponse(User user);

    @Mapping(target = "userType", expression = "java(toReadableType(user.getUserType()))")
    @Mapping(target = "status", expression = "java(toReadableStatus(user.getStatus()))")
    UserProfileResponse.UserInfo toUserInfo(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromRequest(UpdateAccountRequest request, @MappingTarget User user);

    @Mapping(target = "userType", expression = "java(user.getUserType() != null ? user.getUserType().name() : null)")
    @Mapping(target = "status", expression = "java(user.getStatus() != null ? user.getStatus().name() : null)")
    @Mapping(target = "riderProfile", source = "riderProfile")
    @Mapping(target = "driverProfile", source = "driverProfile")
    com.mssus.app.dto.response.UserResponse toUserResponse(User user);

    default java.util.List<String> getUserProfiles(User user) {
        java.util.List<String> profiles = new java.util.ArrayList<>();

        if (user.getRiderProfile() != null) {
            profiles.add("RIDER");
        }

        if (user.getDriverProfile() != null) {
            profiles.add("DRIVER");
        }

        return profiles;
    }

    // Helper for enum conversion
    default UserStatus toUserStatus(String status) {
        if (status == null || status.trim().isEmpty()) return null;
        return UserStatus.valueOf(status.trim().toUpperCase());
    }

    default UserType toUserType(String type) {
        if (type == null || type.trim().isEmpty()) return null;
        return UserType.valueOf(type.trim().toUpperCase());
    }

    default String toReadableType(UserType type) {
        if (type == null) return null;
        // Customize as needed
        return switch (type) {
            case ADMIN -> "Admin";
            case USER -> "User";
        };
    }

    default String toReadableStatus(UserStatus status) {
        if (status == null) return null;
        // Customize as needed
        return switch (status) {
            case EMAIL_VERIFYING -> "Email Verifying";
            case PENDING -> "Pending";
            case ACTIVE -> "Active";
            case SUSPENDED -> "Suspended";
            case REJECTED -> "Rejected";
            case DELETED -> "Deleted";
        };
    }
}