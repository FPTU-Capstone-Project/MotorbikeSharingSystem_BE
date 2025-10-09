package com.mssus.app.service.matching;

import com.mssus.app.dto.notification.DriverRideOfferNotification;
import com.mssus.app.dto.notification.RiderMatchStatusNotification;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.SharedRideRequest;
import lombok.NonNull;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Builds websocket payloads for ride matching events.
 */
@Component
public class MatchingResponseAssembler {

    public DriverRideOfferNotification toDriverOffer(@NonNull SharedRideRequest request,
                                                     @NonNull DriverProfile driver,
                                                     Location pickup,
                                                     Location dropoff,
                                                     @NonNull RideMatchProposalResponse proposal,
                                                     int rank,
                                                     Instant expiresAt) {

        return DriverRideOfferNotification.builder()
                .requestId(request.getSharedRideRequestId())
                .rideId(proposal.getSharedRideId())
                .driverId(driver.getDriverId())
                .driverName(driver.getUser().getFullName())
                .riderId(request.getRider().getRiderId())
                .riderName(request.getRider().getUser().getFullName())
                .pickupLocationName(pickup != null ? pickup.getName() : null)
                .dropoffLocationName(dropoff != null ? dropoff.getName() : null)
                .pickupLat(request.getPickupLat())
                .pickupLng(request.getPickupLng())
                .dropoffLat(request.getDropoffLat())
                .dropoffLng(request.getDropoffLng())
                .pickupTime(request.getPickupTime())
                .fareAmount(request.getFareAmount())
                .matchScore(proposal.getMatchScore())
                .proposalRank(rank)
                .offerExpiresAt(ZonedDateTime.ofInstant(expiresAt, ZoneId.systemDefault()))
                .build();
    }

    public RiderMatchStatusNotification toRiderMatchSuccess(@NonNull SharedRideRequest request,
                                                            @NonNull RideMatchProposalResponse proposal) {
        return RiderMatchStatusNotification.builder()
                .requestId(request.getSharedRideRequestId())
                .status("ACCEPTED")
                .message("Driver accepted your ride request.")
                .rideId(proposal.getSharedRideId())
                .driverId(proposal.getDriverId())
                .driverName(proposal.getDriverName())
                .driverRating(proposal.getDriverRating())
                .vehicleModel(proposal.getVehicleModel())
                .vehiclePlate(proposal.getVehiclePlate())
                .estimatedPickupTime(proposal.getEstimatedPickupTime())
                .estimatedDropoffTime(proposal.getEstimatedDropoffTime())
                .estimatedFare(proposal.getEstimatedFare())
                .build();
    }

    public RiderMatchStatusNotification toRiderNoMatch(@NonNull SharedRideRequest request) {
        return RiderMatchStatusNotification.builder()
                .requestId(request.getSharedRideRequestId())
                .status("NO_MATCH")
                .message("We could not find an available driver. Please try again.")
                .build();
    }
}
