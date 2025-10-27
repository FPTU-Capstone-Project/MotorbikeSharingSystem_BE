package com.mssus.app.controller;

import com.mssus.app.dto.domain.ride.AcceptRequestDto;
import com.mssus.app.dto.domain.ride.BroadcastAcceptRequest;
import com.mssus.app.dto.domain.ride.CreateRideRequestDto;
import com.mssus.app.dto.domain.ride.LatLng;
import com.mssus.app.dto.request.ride.JoinRideRequest;
import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.ride.BroadcastingRideRequestResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.service.SharedRideRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ride-requests")
@RequiredArgsConstructor
@Tag(name = "Ride Requests", description = "Ride request management (AI matching & direct join)")
@SecurityRequirement(name = "bearerAuth")
public class SharedRideRequestController {

    private final SharedRideRequestService requestService;

    @PostMapping
    @PreAuthorize("hasRole('RIDER')")
    @Operation(
            summary = "Create AI-matched ride request (Rider)",
            description = "Create a BOOKING request. Rider must first obtain a quote. " +
                    "System will match with available rides. Wallet hold placed immediately."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Request created successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid quote or location mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a rider or quote ownership mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Quote expired or location not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> bookRide(
            @Valid @RequestBody CreateRideRequestDto request,
            Authentication authentication) {
        log.info("Rider {} creating booking request with quote {}",
                authentication.getName(), request.quoteId());
        SharedRideRequestResponse bookingRequest = requestService.createAIBookingRequest(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingRequest);
    }

    @GetMapping("/broadcasting")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
        summary = "Browse broadcasting requests (Driver)",
        description = "Return rider requests that are currently in broadcasting status for proactive acceptance."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Broadcasting requests retrieved successfully",
            content = @Content(schema = @Schema(implementation = BroadcastingRideRequestResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not a driver",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<BroadcastingRideRequestResponse>> getBroadcastingRequests(
        Authentication authentication) {
        log.info("Driver {} fetching broadcasting ride requests", authentication.getName());
        List<BroadcastingRideRequestResponse> responses = requestService.getBroadcastingRideRequests(authentication);
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/rides/{rideId}")
    @PreAuthorize("hasRole('RIDER')")
    @Operation(
            summary = "Request to join specific ride (Rider)",
            description = "Create a JOIN_RIDE request for a specific ride. " +
                    "Rider must first obtain a quote. Wallet hold placed immediately."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Join request created successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid quote or location mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Insufficient wallet balance",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a rider or quote ownership mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride, quote expired, or location not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "No seats available or invalid ride state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> requestToJoinRide(
            @Parameter(description = "Ride ID to join") @PathVariable Integer rideId,
            @Valid @RequestBody JoinRideRequest request,
            Authentication authentication) {
        log.info("Rider {} requesting to join ride {} with quote {}", 
                authentication.getName(), rideId, request.quoteId());
        SharedRideRequestResponse response = requestService.requestToJoinRide(rideId, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

//    @GetMapping("/{requestId}/matches")
//    @PreAuthorize("hasRole('RIDER')")
//    @Operation(
//            summary = "Get AI match proposals (Rider)",
//            description = "Get matching ride proposals for a pending AI_BOOKING request. " +
//                    "Proposals are scored and ranked."
//    )
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Match proposals retrieved (may be empty)",
//                    content = @Content(schema = @Schema(implementation = RideMatchProposalResponse.class))),
//            @ApiResponse(responseCode = "403", description = "Not the request owner",
//                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
//            @ApiResponse(responseCode = "404", description = "Request not found",
//                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
//            @ApiResponse(responseCode = "409", description = "Request is not in PENDING state or not AI_BOOKING",
//                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
//    })
//    public ResponseEntity<List<RideMatchProposalResponse>> getMatchProposals(
//            @Parameter(description = "Request ID") @PathVariable Integer requestId,
//            Authentication authentication) {
//        log.info("Rider {} fetching match proposals for request {}", authentication.getName(), requestId);
//        List<RideMatchProposalResponse> response = requestService.getMatchProposals(requestId, authentication);
//        return ResponseEntity.ok(response);
//    }

    @GetMapping("/rider/{riderId}")
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    @Operation(
            summary = "Get rider's request history (Rider/Admin)",
            description = "Retrieve all requests for a specific rider with optional status filter"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PageResponse<SharedRideRequestResponse>> getRiderRequests(
            @Parameter(description = "Rider ID") @PathVariable Integer riderId,
            @Parameter(description = "Status filter (PENDING, CONFIRMED, ONGOING, COMPLETED, CANCELLED, EXPIRED)") 
            @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {
        
        log.info("Fetching requests for rider: {}, status: {}", riderId, status);
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        var pageData = requestService.getRequestsByRider(riderId, status, pageable, authentication);
        PageResponse<SharedRideRequestResponse> response = PageResponse.<SharedRideRequestResponse>builder()
                .data(pageData.getContent())
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(pageData.getNumber() + 1)
                        .pageSize(pageData.getSize())
                        .totalPages(pageData.getTotalPages())
                        .totalRecords(pageData.getTotalElements())
                        .build())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN')")
    @Operation(
            summary = "Cancel a ride request (Rider/Admin)",
            description = "Cancel a PENDING or CONFIRMED request. May incur cancellation fee if beyond grace period."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request cancelled successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the request owner or admin",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid request state (can only cancel PENDING/CONFIRMED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> cancelRequest(
            @Parameter(description = "Request ID") @PathVariable Integer requestId,
            Authentication authentication) {
        log.info("User {} cancelling request {}", authentication.getName(), requestId);
        SharedRideRequestResponse response = requestService.cancelRequest(requestId, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rides/{rideId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    @Operation(
            summary = "Get requests for a ride (Driver/Admin)",
            description = "Retrieve all requests for a specific ride with optional status filter"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Requests retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<SharedRideRequestResponse>> getRideRequests(
            @Parameter(description = "Ride ID") @PathVariable Integer rideId,
            @Parameter(description = "Status filter (PENDING, CONFIRMED, ONGOING, COMPLETED, CANCELLED)") 
            @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        log.info("Fetching requests for ride: {}, status: {}", rideId, status);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        var pageData = requestService.getRequestsByRide(rideId, status, pageable, authentication);
        PageResponse<SharedRideRequestResponse> response = PageResponse.<SharedRideRequestResponse>builder()
                .data(pageData.getContent())
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(pageData.getNumber() + 1)
                        .pageSize(pageData.getSize())
                        .totalPages(pageData.getTotalPages())
                        .totalRecords(pageData.getTotalElements())
                        .build())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Accept a ride request (Driver)",
            description = "Accept a PENDING request. For AI_BOOKING: assigns ride and places wallet hold. " +
                    "For JOIN_RIDE: confirms request (hold already placed)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request accepted successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
            @ApiResponse(responseCode = "402", description = "Insufficient rider wallet balance (AI_BOOKING)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the ride owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Request or ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "No seats available or invalid request state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> acceptRequest(
            @Parameter(description = "Request ID") @PathVariable Integer requestId,
            @Valid @RequestBody AcceptRequestDto acceptDto,
            Authentication authentication) {
        log.info("Driver {} accepting request {} for ride {}", 
                authentication.getName(), requestId, acceptDto.rideId());
        SharedRideRequestResponse response = requestService.acceptRequest(requestId, acceptDto, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Reject a ride request (Driver)",
            description = "Reject a PENDING request. Releases wallet hold for JOIN_RIDE."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request rejected successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the ride owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid request state or cannot reject AI_BOOKING without ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> rejectRequest(
            @Parameter(description = "Request ID") @PathVariable Integer requestId,
            @Parameter(description = "Rejection reason") @RequestParam @NotBlank String reason,
            Authentication authentication) {
        log.info("Driver {} rejecting request {} - reason: {}", authentication.getName(), requestId, reason);
        SharedRideRequestResponse response = requestService.rejectRequest(requestId, reason, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{requestId}/broadcast/accept")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
        summary = "Accept a broadcasted ride request (Driver)",
        description = "Claim a broadcasted AI booking request and create a new shared ride."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Broadcast request accepted successfully",
            content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not an active driver",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Request or vehicle not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Broadcast window closed or already claimed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> acceptBroadcastRequest(
        @Parameter(description = "Request ID") @PathVariable Integer requestId,
        @Valid @RequestBody BroadcastAcceptRequest request,
        Authentication authentication) {
        log.info("Driver {} accepting broadcast request {}", authentication.getName(), requestId);
        SharedRideRequestResponse response = requestService.acceptBroadcast(requestId, request, authentication);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get request details (Any authenticated user)",
            description = "Retrieve detailed information about a specific ride request"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
            @ApiResponse(responseCode = "404", description = "Request not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> getRequestById(
            @Parameter(description = "Request ID") @PathVariable Integer requestId) {
        log.info("Fetching request: {}", requestId);
        SharedRideRequestResponse response = requestService.getRequestById(requestId);
        return ResponseEntity.ok(response);
    }
}

