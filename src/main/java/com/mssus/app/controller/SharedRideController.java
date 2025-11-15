package com.mssus.app.controller;

import com.mssus.app.dto.request.ride.CompleteRideReqRequest;
import com.mssus.app.dto.request.ride.CreateRideRequest;
import com.mssus.app.dto.request.ride.StartRideRequest;
import com.mssus.app.dto.request.ride.StartRideReqRequest;
import com.mssus.app.dto.request.report.RideReportCreateRequest;
import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.ride.RideCompletionResponse;
import com.mssus.app.dto.response.ride.SharedRideRequestResponse;
import com.mssus.app.dto.response.ride.SharedRideResponse;
import com.mssus.app.dto.response.ride.TrackingResponse;
import com.mssus.app.dto.response.report.UserReportResponse;
import com.mssus.app.dto.domain.ride.LocationPoint;
import com.mssus.app.service.RideTrackingService;
import com.mssus.app.service.SharedRideService;
import com.mssus.app.service.UserReportService;
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
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Tag(name = "Shared Rides", description = "Shared ride lifecycle management")
@SecurityRequirement(name = "bearerAuth")
public class SharedRideController {

    private final SharedRideService sharedRideService;
    private final RideTrackingService rideTrackingService;
    private final UserReportService userReportService;

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Create a new shared ride (Driver)",
            description = "Driver creates a new shared ride. Route will be validated via OSRM."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ride created successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or route validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not a driver or vehicle not owned",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle or location not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideResponse> createRide(
            @Valid @RequestBody CreateRideRequest request,
            Authentication authentication) {
        log.info("Driver {} creating new ride", authentication.getName());
        SharedRideResponse response = sharedRideService.createRide(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/driver/{driverId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    @Operation(
            summary = "Get rides for a driver (Driver/Admin)",
            description = "Retrieve all rides for a specific driver with optional status filter"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rides retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<SharedRideResponse>> getDriverRides(
            @Parameter(description = "Driver ID") @PathVariable Integer driverId,
            @Parameter(description = "Status filter (SCHEDULED, ONGOING, COMPLETED, CANCELLED)") 
            @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "scheduledDepartureTime") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {
        
        log.info("Fetching rides for driver: {}, status: {}", driverId, status);
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        var pageData = sharedRideService.getRidesByDriver(driverId, status, pageable, authentication);
        PageResponse<SharedRideResponse> response = PageResponse.<SharedRideResponse>builder()
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

    @GetMapping("/my-completed-rides")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Get my completed rides (Driver)",
            description = "Retrieve all completed rides for the authenticated driver. Requires login."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Completed rides retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Login required"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Driver role required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<SharedRideResponse>> getMyCompletedRides(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "completedAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {
        log.info("Driver {} fetching their completed rides", authentication.getName());
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<SharedRideResponse> pageData = sharedRideService.getMyCompletedRides(pageable, authentication);
        PageResponse<SharedRideResponse> response = PageResponse.<SharedRideResponse>builder()
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

    @GetMapping()
    @PreAuthorize("hasAnyRole('DRIVER')")
    @Operation(
        summary = "Get rides for logged-in driver (Driver)",
        description = "Retrieve all rides for a the driver with optional status filter"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rides retrieved successfully",
            content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Driver not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<SharedRideResponse>> getDriverRides(
        @Parameter(description = "Status filter (SCHEDULED, ONGOING, COMPLETED, CANCELLED)")
        @RequestParam(required = false) String status,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
        Authentication authentication) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        var pageData = sharedRideService.getRidesByDriver(status, pageable, authentication);
        PageResponse<SharedRideResponse> response = PageResponse.<SharedRideResponse>builder()
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

    @PostMapping("/{rideId}/start")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Start a ride (Driver)",
            description = "Transition ride from SCHEDULED to ONGOING. Requires at least one confirmed request."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride started successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the ride owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid ride state or no confirmed passengers",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideResponse> startRide(
            @Valid @RequestBody StartRideRequest request,
            Authentication authentication) {
        log.info("Driver {} starting ride {}", authentication.getName(), request.rideId());
        SharedRideResponse response = sharedRideService.startRide(request, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/start-ride-request")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
        summary = "Start a specific ride request (Driver)",
        description = "Marks a CONFIRMED ride request as ONGOING once the rider is picked up."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ride request started successfully",
            content = @Content(schema = @Schema(implementation = SharedRideRequestResponse.class))),
        @ApiResponse(responseCode = "403", description = "Not the ride owner",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Ride or ride request not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Invalid ride/request state",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideRequestResponse> startRideRequest(
        @Valid @RequestBody StartRideReqRequest request,
        Authentication authentication) {
        SharedRideRequestResponse response = sharedRideService.startRideRequestOfRide(request, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "Complete a ride (Driver)",
            description = "Complete a specific ride request and automatically finalize the ride when all requests are done."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride completed successfully",
                    content = @Content(schema = @Schema(implementation = RideCompletionResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the ride owner",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid ride/request state",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RideCompletionResponse> completeRide(
            @Valid @RequestBody CompleteRideReqRequest request,
            Authentication authentication) {
        log.info("Driver {} completing ride {} request {}", authentication.getName(), request.rideId(), request.rideRequestId());
        RideCompletionResponse response = sharedRideService.completeRide(request, authentication);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{rideId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    @Operation(
            summary = "Cancel a ride (Driver/Admin)",
            description = "Cancel a scheduled ride. Releases all holds for confirmed requests."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride cancelled successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not the ride owner or admin",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid ride state (can only cancel SCHEDULED)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideResponse> cancelRide(
            @Parameter(description = "Ride ID") @PathVariable Integer rideId,
            @Parameter(description = "Cancellation reason") @RequestParam @NotBlank String reason,
            Authentication authentication) {
        log.info("User {} cancelling ride {}", authentication.getName(), rideId);
        SharedRideResponse response = sharedRideService.cancelRide(rideId, reason, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{rideId}/report")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Submit a report for a completed ride",
            description = "Submit a report about issues encountered during a completed ride. Reports can only be submitted for completed rides within 7 days of completion."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Report submitted successfully",
                    content = @Content(schema = @Schema(implementation = UserReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not authorized to report this ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Report already exists for this ride",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserReportResponse> submitRideReport(
            @Parameter(description = "Ride ID") @PathVariable Integer rideId,
            @Valid @RequestBody RideReportCreateRequest request,
            Authentication authentication) {
        log.info("User {} submitting report for ride {}", authentication.getName(), rideId);
        UserReportResponse response = userReportService.submitRideReport(rideId, authentication, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/available")
    @PreAuthorize("hasRole('RIDER')")
    @Operation(
            summary = "Browse available rides (Rider)",
            description = "Search for rides with available seats in specified time window"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Available rides retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PageResponse<SharedRideResponse>> browseAvailableRides(
            @Parameter(description = "Start time (ISO 8601)", example = "2025-10-05T08:00:00") 
            @RequestParam(required = false) String startTime,
            @Parameter(description = "End time (ISO 8601)", example = "2025-10-05T10:00:00") 
            @RequestParam(required = false) String endTime,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
        
        log.info("Browsing available rides - startTime: {}, endTime: {}", startTime, endTime);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "scheduledTime"));
        
        var pageData = sharedRideService.browseAvailableRides(startTime, endTime, pageable);
        PageResponse<SharedRideResponse> response = PageResponse.<SharedRideResponse>builder()
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

    @GetMapping("/{rideId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get ride details (Any authenticated user)",
            description = "Retrieve detailed information about a specific ride"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ride retrieved successfully",
                    content = @Content(schema = @Schema(implementation = SharedRideResponse.class))),
            @ApiResponse(responseCode = "404", description = "Ride not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SharedRideResponse> getRideById(
            @Parameter(description = "Ride ID") @PathVariable Integer rideId) {
        log.info("Fetching ride: {}", rideId);
        SharedRideResponse response = sharedRideService.getRideById(rideId);
        return ResponseEntity.ok(response);
    }


}
