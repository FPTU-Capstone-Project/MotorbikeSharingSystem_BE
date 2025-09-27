package com.mssus.app.mapper;

import com.mssus.app.dto.response.StudentVerificationResponse;
import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.dto.response.VehicleResponse;
import com.mssus.app.entity.Verification;
import com.mssus.app.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface VehicleMapper {
    VehicleMapper INSTANCE = Mappers.getMapper(VehicleMapper.class);

    @Mapping(source = "driver.driverId", target = "driverId")
    VehicleResponse mapToVehicleResponse(Vehicle vehicle);

}
