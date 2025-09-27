package com.mssus.app.controller;

import com.mssus.app.common.enums.OtpFor;
import com.mssus.app.dto.request.GetOtpRequest;
import com.mssus.app.dto.request.OtpRequest;
import com.mssus.app.dto.response.ErrorResponse;
import com.mssus.app.dto.response.OtpResponse;
import com.mssus.app.service.OtpService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
@Tag(name = "OTP", description = "One-Time Password")
public class OtpController {
    private final OtpService otpService;

    @Operation(summary = "Get OTP Code", description = "Request an OTP for verification",
        security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent successfully",
            content = @Content(schema = @Schema(implementation = OtpResponse.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OtpResponse> getOtp(@Valid @RequestBody GetOtpRequest request) {
        OtpResponse response = otpService.requestOtp(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Submit OTP Code", description = "Submit an OTP code for verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP verified successfully",
            content = @Content(schema = @Schema(implementation = OtpResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid or expired OTP",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/verify")
    public ResponseEntity<OtpResponse> submitOtp(@Valid @RequestBody OtpRequest request) {
        OtpResponse response = otpService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }
}
