package com.mssus.app.mapper;

import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.entity.SharedRideRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SharedRideRequestMapper {

    @Mapping(source = "requestKind", target = "requestKind")
    @Mapping(source = "sharedRide.sharedRideId", target = "sharedRideId")
    @Mapping(source = "rider.riderId", target = "riderId")
    @Mapping(source = "rider.user.fullName", target = "riderName")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "pickupLocationName", ignore = true)   // Set manually in service layer
    @Mapping(target = "dropoffLocationName", ignore = true)  // Set manually in service layer
    @Mapping(target = "pickupLat", ignore = true)          // Set manually in service layer
    @Mapping(target = "pickupLng", ignore = true)          // Set manually in service layer
    @Mapping(target = "dropoffLat", ignore = true)         // Set manually in service layer
    @Mapping(target = "dropoffLng", ignore = true)         //
    SharedRideRequestResponse toResponse(SharedRideRequest request);

    @Mapping(source = "requestKind", target = "requestKind")
    @Mapping(source = "sharedRide.sharedRideId", target = "sharedRideId")
    @Mapping(source = "rider.riderId", target = "riderId")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "riderName", ignore = true)            // Set manually in service layer
    @Mapping(target = "riderRating", ignore = true)          // Set manually in service layer
    @Mapping(target = "pickupLocationName", ignore = true)   // Set manually in service layer
    @Mapping(target = "dropoffLocationName", ignore = true)  // Set manually in service layer
    SharedRideRequestResponse toLightweightResponse(SharedRideRequest request);
}

