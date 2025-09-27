package com.mssus.app.mapper;

import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.entity.AdminProfile;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.Wallet;
import org.mapstruct.Mapper;
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
}
