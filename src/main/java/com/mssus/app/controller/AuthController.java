package com.mssus.app.controller;

import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.service.AuthService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Account & Personal Information Management")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register", description = "Create a new user account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Account created successfully",
                content = @Content(schema = @Schema(implementation = RegisterResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email or phone already exists",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/auth/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for email: {}", request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login", description = "Authenticate an existing user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
                content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for: {}", request.getEmailOrPhone());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout", description = "Invalidate the current authentication token",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logged out successfully",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/auth/logout")
    public ResponseEntity<MessageResponse> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        MessageResponse response = authService.logout(token);
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
    @GetMapping("/users/me")
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        String username = authentication.getName();
        UserProfileResponse response = authService.getCurrentUserProfile(username);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update Profile", description = "Update the authenticated user's basic information",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/users/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        String username = authentication.getName();
        UserProfileResponse response = authService.updateProfile(username, request);
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
    @PutMapping("/users/me/update-password")
    public ResponseEntity<MessageResponse> updatePassword(
            Authentication authentication,
            @Valid @RequestBody UpdatePasswordRequest request) {
        String username = authentication.getName();
        MessageResponse response = authService.updatePassword(username, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Forgot Password", description = "Initiate a password reset")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent to registered contact",
                content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    })
    @PostMapping("/users/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        MessageResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get OTP Code", description = "Request an OTP for verification",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent successfully",
                content = @Content(schema = @Schema(implementation = OtpResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/otp")
    public ResponseEntity<OtpResponse> getOtp(
            Authentication authentication,
            @Parameter(description = "OTP purpose", required = true)
            @RequestParam String otpFor) {
        String username = authentication.getName();
        OtpResponse response = authService.requestOtp(username, otpFor);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Submit OTP Code", description = "Submit an OTP code for verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP verified successfully",
                content = @Content(schema = @Schema(implementation = OtpResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid or expired OTP",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/otp")
    public ResponseEntity<OtpResponse> submitOtp(@Valid @RequestBody OtpRequest request) {
        OtpResponse response = authService.verifyOtp(request);
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
    @PutMapping(value = "/users/me/update-avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> updateAvatar(
            Authentication authentication,
            @Parameter(description = "Avatar image file", required = true)
            @RequestParam("avatar") MultipartFile file) {
        String username = authentication.getName();
        MessageResponse response = authService.updateAvatar(username, file);
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
    @PostMapping("/users/me/switch-profile")
    public ResponseEntity<MessageResponse> switchProfile(
            Authentication authentication,
            @Valid @RequestBody SwitchProfileRequest request) {
        String username = authentication.getName();
        MessageResponse response = authService.switchProfile(username, request);
        return ResponseEntity.ok(response);
    }

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
    @PostMapping(value = "/users/me/student-verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submitStudentVerification(
            Authentication authentication,
            @Parameter(description = "Student ID document", required = true)
            @RequestParam("document") MultipartFile document) {
        String username = authentication.getName();
        VerificationResponse response = authService.submitStudentVerification(username, document);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Submit Driver Verification", 
            description = "Submit documents required to become a driver",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Driver verification submitted successfully",
                content = @Content(schema = @Schema(implementation = VerificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid documents or information",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Driver profile already exists",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/users/me/driver-verifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationResponse> submitDriverVerification(
            Authentication authentication,
            @ModelAttribute @Valid DriverVerificationRequest request) {
        String username = authentication.getName();
        VerificationResponse response = authService.submitDriverVerification(username, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
