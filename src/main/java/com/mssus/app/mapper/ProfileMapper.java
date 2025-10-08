package com.mssus.app.mapper;

import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.dto.response.UserResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ProfileMapper {

    UserProfileResponse.RiderProfile toRiderProfileResponse(RiderProfile entity);

    UserProfileResponse.DriverProfile toDriverProfileResponse(DriverProfile entity);

//    UserProfileResponse.AdminProfile toAdminProfileResponse(AdminProfile entity);

    UserProfileResponse.WalletInfo toWalletInfoResponse(Wallet entity);

    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    @Mapping(target = "preferredPaymentMethod", expression = "java(entity.getPreferredPaymentMethod() != null ? entity.getPreferredPaymentMethod().name() : null)")
    UserResponse.RiderProfileInfo toRiderProfileInfo(RiderProfile entity);

    @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    UserResponse.DriverProfileInfo toDriverProfileInfo(DriverProfile entity);
}
