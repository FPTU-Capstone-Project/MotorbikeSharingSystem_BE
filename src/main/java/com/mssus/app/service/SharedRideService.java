package com.mssus.app.service;

import com.mssus.app.dto.request.ride.CompleteRideReqRequest;
import com.mssus.app.dto.request.ride.CompleteRideRequest;
import com.mssus.app.dto.request.ride.CreateRideRequest;
import com.mssus.app.dto.request.ride.StartRideRequest;
import com.mssus.app.dto.request.ride.StartRideReqRequest;
import com.mssus.app.dto.response.ride.RideCompletionResponse;
import com.mssus.app.dto.response.ride.RideRequestCompletionResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.dto.response.ride.SharedRideResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface SharedRideService {
    SharedRideResponse createRide(CreateRideRequest request, Authentication authentication);

    SharedRideResponse getRideById(Integer rideId);

    Page<SharedRideResponse> getRidesByDriver(Integer driverId, String status, Pageable pageable, Authentication authentication);

    Page<SharedRideResponse> getRidesByDriver(String status, Pageable pageable, Authentication authentication);

    Page<SharedRideResponse> browseAvailableRides(String startTime, String endTime, Pageable pageable);

    SharedRideResponse startRide(StartRideRequest request, Authentication authentication);

    SharedRideRequestResponse startRideRequestOfRide(StartRideReqRequest request, Authentication authentication);

    RideRequestCompletionResponse completeRideRequestOfRide(CompleteRideReqRequest request, Authentication authentication);

    RideCompletionResponse completeRide(CompleteRideRequest request, Authentication authentication);

    SharedRideResponse cancelRide(Integer rideId, String reason, Authentication authentication);
}

