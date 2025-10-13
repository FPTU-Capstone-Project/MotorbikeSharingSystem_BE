package com.mssus.app.controller;

import com.mssus.app.dto.request.SwitchProfileRequest;
import com.mssus.app.dto.request.UpdatePasswordRequest;
import com.mssus.app.dto.request.UpdateProfileRequest;
import com.mssus.app.dto.response.*;
import com.mssus.app.service.ProfileService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final ProfileService profileService;

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
        Authentication authentication,
        @Valid @RequestBody UpdateProfileRequest request) {
        String username = authentication.getName();
        UserProfileResponse response = profileService.updateProfile(username, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update Password", description = "Change the authenticated user's password",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password updated successfully",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Old password incorrect",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/update-password")
    public ResponseEntity<MessageResponse> updatePassword(
        Authentication authentication,
        @Valid @RequestBody UpdatePasswordRequest request) {
        String username = authentication.getName();
        MessageResponse response = profileService.updatePassword(username, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Profile", description = "Retrieve the authenticated user's profile",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        String username = authentication.getName();
        UserProfileResponse response = profileService.getCurrentUserProfile(username);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Switch Profile", description = "Switch between rider and driver profiles",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile switched successfully",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid target role or profile not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/switch-profile")
    public ResponseEntity<SwitchProfileResponse> switchProfile(
        Authentication authentication,
        @Valid @RequestBody SwitchProfileRequest request) {
        String username = authentication.getName();
        SwitchProfileResponse response = profileService.switchProfile(username, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update Profile Avatar", description = "Upload or replace the user's profile picture",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Avatar updated successfully",
            content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid file",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/update-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> updateAvatar(
        Authentication authentication,
        @Parameter(description = "Avatar image file", required = true)
        @RequestParam("avatar") MultipartFile file) {
        String username = authentication.getName();
        MessageResponse response = profileService.updateAvatar(username, file);
        return ResponseEntity.ok(response);
    }
    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Submit Driver License",
        description = "Submit driver license images for verification",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Driver license submitted successfully",
            content = @Content(schema = @Schema(implementation = VerificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid documents or information",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/driver-verifications/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submitDriverLicense(
        Authentication authentication,
        @Parameter(description = "Driver license images", required = true)
        @RequestParam("documents") List<MultipartFile> documents) {
        String username = authentication.getName();
        VerificationResponse response = profileService.submitDriverLicense(username, documents);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Submit Driver Documents",
        description = "Submit additional driver documents for verification",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Driver documents submitted successfully",
            content = @Content(schema = @Schema(implementation = VerificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid documents or information",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/driver-verifications/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submitDriverDocuments(
        Authentication authentication,
        @Parameter(description = "Driver supporting documents", required = true)
        @RequestParam("documents") List<MultipartFile> documents) {
        String username = authentication.getName();
        VerificationResponse response = profileService.submitDriverDocuments(username, documents);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('RIDER')")
    @Operation(summary = "Submit Vehicle Registration",
        description = "Submit vehicle registration documents for verification",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Vehicle registration submitted successfully",
            content = @Content(schema = @Schema(implementation = VerificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid documents or information",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/driver-verifications/vehicle-registration", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submitVehicleRegistration(
        Authentication authentication,
        @Parameter(description = "Vehicle registration documents", required = true)
        @RequestParam("documents") List<MultipartFile> documents) {
        String username = authentication.getName();
        VerificationResponse response = profileService.submitVehicleRegistration(username, documents);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Submit Student Verification",
        description = "Upload documentation to verify student status",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Verification submitted successfully",
            content = @Content(schema = @Schema(implementation = VerificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid document",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Already verified",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/student-verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submitStudentVerification(
        Authentication authentication,
        @Parameter(description = "Student ID document", required = true)
        @RequestParam("document") List<MultipartFile> document
    ) {
        String username = authentication.getName();
        VerificationResponse response = profileService.submitStudentVerification(username, document);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get All Users",
            description = "Retrieve paginated list of all users with their profile details (Admin only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort.Direction sort = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort, sortBy));
        PageResponse<UserResponse> response = profileService.getAllUsers(pageable);
        return ResponseEntity.ok(response);
    }

}
