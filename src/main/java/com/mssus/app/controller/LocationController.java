package com.mssus.app.controller;

import com.mssus.app.dto.response.LocationResponse;
import com.mssus.app.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Tag(name = "Locations", description = "Points of Interest management")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/pois")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Get Points of Interest",
        description = "Retrieve all available points of interest for the application",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "POIs retrieved successfully",
            content = @Content(schema = @Schema(implementation = LocationResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<LocationResponse>> getPOIs() {
        log.info("Fetching all points of interest");
        List<LocationResponse> response = locationService.getAppPOIs();
        return ResponseEntity.ok(response);
    }
}
