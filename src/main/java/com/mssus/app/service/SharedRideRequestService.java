package com.mssus.app.service;

import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.dto.response.ride.BroadcastingRideRequestResponse;
import com.mssus.app.dto.domain.ride.AcceptRequestDto;
import com.mssus.app.dto.domain.ride.BroadcastAcceptRequest;
import com.mssus.app.dto.domain.ride.CreateRideRequestDto;
import com.mssus.app.dto.request.ride.JoinRideRequest;
import com.mssus.app.dto.response.ride.RideMatchProposalResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface SharedRideRequestService {

    SharedRideRequestResponse createAIBookingRequest(CreateRideRequestDto request, Authentication authentication);

    SharedRideRequestResponse requestToJoinRide(Integer rideId, JoinRideRequest request, Authentication authentication);

    SharedRideRequestResponse getRequestById(Integer requestId);

    List<RideMatchProposalResponse> getMatchProposals(Integer requestId, Authentication authentication);

    Page<SharedRideRequestResponse> getRequestsByRider(Integer riderId, String status, Pageable pageable, Authentication authentication);

    Page<SharedRideRequestResponse> getRequestsByRide(Integer rideId, String status, Pageable pageable, Authentication authentication);

    SharedRideRequestResponse acceptRequest(Integer requestId, AcceptRequestDto acceptDto, Authentication authentication);

    SharedRideRequestResponse rejectRequest(Integer requestId, String reason, Authentication authentication);

    SharedRideRequestResponse cancelRequest(Integer requestId, Authentication authentication);

    SharedRideRequestResponse acceptBroadcast(Integer requestId, BroadcastAcceptRequest request, Authentication authentication);

    List<BroadcastingRideRequestResponse> getBroadcastingRideRequests(Authentication authentication);
}

