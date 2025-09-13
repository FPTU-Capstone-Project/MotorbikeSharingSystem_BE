package com.mssus.app.mapper;

import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.entity.AdminProfileEntity;
import com.mssus.app.entity.DriverProfileEntity;
import com.mssus.app.entity.RiderProfileEntity;
import com.mssus.app.entity.WalletEntity;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ProfileMapper {

    UserProfileResponse.RiderProfile toRiderProfileResponse(RiderProfileEntity entity);

    UserProfileResponse.DriverProfile toDriverProfileResponse(DriverProfileEntity entity);

    UserProfileResponse.AdminProfile toAdminProfileResponse(AdminProfileEntity entity);

    UserProfileResponse.WalletInfo toWalletInfoResponse(WalletEntity entity);
}
