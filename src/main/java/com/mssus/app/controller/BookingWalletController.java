package com.mssus.app.controller;

import com.mssus.app.dto.request.wallet.*;
import com.mssus.app.dto.response.wallet.BalanceCheckResponse;
import com.mssus.app.dto.response.wallet.WalletOperationResponse;
import com.mssus.app.service.BookingWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/wallet")
@RequiredArgsConstructor
@Tag(name = "Booking Wallet", description = "Internal wallet operations for booking service")
public class BookingWalletController {

    private final BookingWalletService bookingWalletService;

    @Operation(
            summary = "Hold funds",
            description = "Hold funds from user wallet for a booking. This creates a pending transaction that reserves the amount."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Funds held successfully",
                    content = @Content(schema = @Schema(implementation = WalletOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient balance"),
            @ApiResponse(responseCode = "404", description = "User wallet not found")
    })
    @PostMapping("/hold")
    public ResponseEntity<WalletOperationResponse> holdFunds(
            @Valid @RequestBody WalletHoldRequest request) {
        log.info("Hold funds request - userId: {}, bookingId: {}, amount: {}",
                request.getUserId(), request.getBookingId(), request.getAmount());

        WalletOperationResponse response = bookingWalletService.holdFunds(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Capture held funds",
            description = "Capture previously held funds and transfer to driver. This finalizes the payment for a completed booking."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Funds captured successfully",
                    content = @Content(schema = @Schema(implementation = WalletOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or no held funds"),
            @ApiResponse(responseCode = "404", description = "User or booking not found")
    })
    @PostMapping("/capture")
    public ResponseEntity<WalletOperationResponse> captureFunds(
            @Valid @RequestBody WalletCaptureRequest request) {
        log.info("Capture funds request - userId: {}, bookingId: {}, amount: {}, driverId: {}",
                request.getUserId(), request.getBookingId(), request.getAmount(), request.getDriverId());

        WalletOperationResponse response = bookingWalletService.captureFunds(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Release held funds",
            description = "Release previously held funds back to user. Used when a booking is cancelled before completion."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Funds released successfully",
                    content = @Content(schema = @Schema(implementation = WalletOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or no held funds"),
            @ApiResponse(responseCode = "404", description = "User or booking not found")
    })
    @PostMapping("/release")
    public ResponseEntity<WalletOperationResponse> releaseFunds(
            @Valid @RequestBody WalletReleaseRequest request) {
        log.info("Release funds request - userId: {}, bookingId: {}, amount: {}",
                request.getUserId(), request.getBookingId(), request.getAmount());

        WalletOperationResponse response = bookingWalletService.releaseFunds(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Refund to user",
            description = "Refund amount to user wallet. Used for service issues or cancellations after payment."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund processed successfully",
                    content = @Content(schema = @Schema(implementation = WalletOperationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "User or booking not found")
    })
    @PostMapping("/refund")
    public ResponseEntity<WalletOperationResponse> refundToUser(
            @Valid @RequestBody WalletRefundRequest request) {
        log.info("Refund request - userId: {}, bookingId: {}, amount: {}, reason: {}",
                request.getUserId(), request.getBookingId(), request.getAmount(), request.getReason());

        WalletOperationResponse response = bookingWalletService.refundToUser(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Check balance",
            description = "Check if user has sufficient balance for a booking amount"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance check completed",
                    content = @Content(schema = @Schema(implementation = BalanceCheckResponse.class))),
            @ApiResponse(responseCode = "404", description = "User wallet not found")
    })
    @GetMapping("/check-balance")
    public ResponseEntity<BalanceCheckResponse> checkBalance(
            @Parameter(description = "User ID", required = true) @RequestParam Integer userId,
            @Parameter(description = "Required amount", required = true) @RequestParam BigDecimal amount) {
        log.info("Check balance request - userId: {}, requiredAmount: {}", userId, amount);

        BalanceCheckResponse response = bookingWalletService.checkBalance(userId, amount);
        return ResponseEntity.ok(response);
    }
}