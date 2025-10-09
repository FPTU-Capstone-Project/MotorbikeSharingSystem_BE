package com.mssus.app.controller;

import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.*;
import com.mssus.app.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/verification")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Verification", description = "Student and Driver verification management")
public class VerificationController {

    private final VerificationService verificationService;


    @GetMapping("/all")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verifications retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<VerificationResponse>> getAllVerifications(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir

    ){
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        PageResponse<VerificationResponse> response = verificationService.getAllVerifications(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/students/{id}")
    @Operation(summary = "Get student verification details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Student verification found",
                    content = @Content(schema = @Schema(implementation = StudentVerificationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Student verification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<StudentVerificationResponse> getStudentVerificationById(
            @Parameter(description = "User ID") @PathVariable Integer id) {
        StudentVerificationResponse response = verificationService.getStudentVerificationById(id);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/bulk-approve")
    @Operation(summary = "Bulk approve student verifications")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bulk approval completed",
                    content = @Content(schema = @Schema(implementation = BulkOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<BulkOperationResponse> bulkApproveStudentVerifications(
            Authentication authentication,
            @Valid @RequestBody BulkApprovalRequest request) {
        BulkOperationResponse response = verificationService.bulkApproveVerifications(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drivers/{id}/kyc")
    @Operation(summary = "Get driver KYC details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver KYC found",
                    content = @Content(schema = @Schema(implementation = DriverKycResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DriverKycResponse> getDriverKycById(
            @Parameter(description = "Driver ID") @PathVariable Integer id) {
        DriverKycResponse response = verificationService.getDriverKycById(id);
        return ResponseEntity.ok(response);
    }


    @PostMapping("/approve-vehicle")
    @Operation(summary = "Approve driver vehicle")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver vehicle approved",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Vehicle verification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> approveDriverVehicle(
            Authentication authentication,
            @Valid @RequestBody VerificationDecisionRequest request) {
        MessageResponse response = verificationService.approveDriverVehicle(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reject")
    @Operation(summary = "Reject verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification rejected",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request - rejection reason required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Verifications not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> rejectDriverVerification(
            Authentication authentication,
            @Valid @RequestBody VerificationDecisionRequest request) {
        MessageResponse response = verificationService.rejectVerification(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/drivers/{id}/background-check")
    @Operation(summary = "Update driver background check")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Background check updated successfully",
                    content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Driver not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> updateBackgroundCheck(
            Authentication authentication,
            @Parameter(description = "Driver ID") @PathVariable Integer id,
            @Valid @RequestBody BackgroundCheckRequest request) {
        MessageResponse response = verificationService.updateBackgroundCheck(authentication.getName(), id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drivers/stats")
    @Operation(summary = "Get driver verification statistics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver verification statistics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DriverStatsResponse.class)))
    })
    public ResponseEntity<DriverStatsResponse> getDriverVerificationStats() {
        DriverStatsResponse response = verificationService.getDriverVerificationStats();
        return ResponseEntity.ok(response);
    }
}