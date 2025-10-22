package com.mssus.app.mapper;

import com.mssus.app.dto.response.ride.SharedRideResponse;
import com.mssus.app.entity.SharedRide;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SharedRideMapper {
    @Mapping(source = "driver.driverId", target = "driverId")
    @Mapping(source = "driver.user.fullName", target = "driverName")
    @Mapping(source = "driver.ratingAvg", target = "driverRating")
    @Mapping(source = "vehicle.vehicleId", target = "vehicleId")
    @Mapping(source = "vehicle.model", target = "vehicleModel")
    @Mapping(source = "vehicle.plateNumber", target = "vehiclePlate")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "availableSeats", expression = "java(ride.getMaxPassengers() - ride.getCurrentPassengers())")
    @Mapping(source = "startLocation", target = "startLocation") // Set manually in service layer
    @Mapping(source = "endLocation", target = "endLocation")   // Set manually in service layer
    SharedRideResponse toResponse(SharedRide ride);

    @Mapping(source = "driver.driverId", target = "driverId")
    @Mapping(source = "vehicle.vehicleId", target = "vehicleId")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "availableSeats", expression = "java(ride.getMaxPassengers() - ride.getCurrentPassengers())")
    @Mapping(target = "driverName", ignore = true)        // Set manually in service layer
    @Mapping(target = "driverRating", ignore = true)      // Set manually in service layer
    @Mapping(target = "vehicleModel", ignore = true)      // Set manually in service layer
    @Mapping(target = "vehiclePlate", ignore = true)      // Set manually in service layer
//    @Mapping(target = "startLocationName", ignore = true) // Set manually in service layer
//    @Mapping(target = "endLocationName", ignore = true)   // Set manually in service layer
    SharedRideResponse toLightweightResponse(SharedRide ride);
}

