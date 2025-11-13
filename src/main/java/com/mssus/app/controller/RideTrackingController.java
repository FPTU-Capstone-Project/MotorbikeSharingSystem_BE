package com.mssus.app.controller;

import com.mssus.app.dto.response.ride.RideTrackingSnapshotResponse;
import com.mssus.app.service.RideTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ride-tracking")
@RequiredArgsConstructor
@Tag(name = "Ride Tracking", description = "Ride tracking utilities")
@SecurityRequirement(name = "bearerAuth")
public class RideTrackingController {

    private final RideTrackingService rideTrackingService;

    @GetMapping("/{rideId}/snapshot")
    @PreAuthorize("hasAnyRole('RIDER', 'DRIVER', 'ADMIN')")
    @Operation(
        summary = "Get tracking snapshot for a ride",
        description = "Returns the driver's latest location and the intended polyline so clients can restore tracking state."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tracking snapshot returned"),
        @ApiResponse(responseCode = "404", description = "Ride not found"),
        @ApiResponse(responseCode = "403", description = "Unauthorized to view ride tracking data")
    })
    public ResponseEntity<RideTrackingSnapshotResponse> getTrackingSnapshot(
        @PathVariable Integer rideId,
        Authentication authentication) {
        return ResponseEntity.ok(rideTrackingService.getTrackingSnapshot(rideId, authentication));
    }
}

