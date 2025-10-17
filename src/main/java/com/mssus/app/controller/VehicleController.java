package com.mssus.app.controller;

import com.mssus.app.dto.request.CreateVehicleRequest;
import com.mssus.app.dto.request.UpdateVehicleRequest;
import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.VehicleResponse;
import com.mssus.app.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @Operation(summary = "Create a new vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vehicle created successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Vehicle with plate number already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> createVehicle(
            @Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse response = vehicleService.createVehicle(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{vehicleId}")
    @Operation(summary = "Get vehicle by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle found",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> getVehicleById(
            @Parameter(description = "Vehicle ID") @PathVariable Integer vehicleId) {
        VehicleResponse response = vehicleService.getVehicleById(vehicleId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{vehicleId}")
    @Operation(summary = "Update vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle updated successfully",
                    content = @Content(schema = @Schema(implementation = VehicleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Vehicle with plate number already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<VehicleResponse> updateVehicle(
            @Parameter(description = "Vehicle ID") @PathVariable Integer vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request) {
        VehicleResponse response = vehicleService.updateVehicle(vehicleId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{vehicleId}")
    @Operation(summary = "Delete vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicle deleted successfully",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> deleteVehicle(
            @Parameter(description = "Vehicle ID") @PathVariable Integer vehicleId) {
        MessageResponse response = vehicleService.deleteVehicle(vehicleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get all vehicles with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicles retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<VehicleResponse>> getAllVehicles(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        PageResponse<VehicleResponse> response = vehicleService.getAllVehicles(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/driver")
    @Operation(summary = "Get vehicles by driver ID with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver vehicles retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<VehicleResponse>> getVehiclesByDriverId(
            Authentication authentication,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        String driverId = authentication.getName();
        PageResponse<VehicleResponse> response = vehicleService.getVehiclesByDriverId(driverId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get vehicles by status with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vehicles by status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    public ResponseEntity<PageResponse<VehicleResponse>> getVehiclesByStatus(
            @Parameter(description = "Vehicle status") @PathVariable String status,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {

        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        PageResponse<VehicleResponse> response = vehicleService.getVehiclesByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }
}
