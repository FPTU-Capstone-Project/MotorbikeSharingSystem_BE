package com.mssus.app.controller;

import com.mssus.app.dto.request.wallet.PayoutInitRequest;
import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.*;
import com.mssus.app.service.TransactionService;
import com.mssus.app.service.WalletService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "User wallet management operations")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;
    @Operation(summary = "Get wallet balance", description = "Retrieve current wallet balance and summary")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully",
                    content = @Content(schema = @Schema(implementation = WalletResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getBalance(Authentication authentication) {
        log.info("Get balance request from user: {}", authentication.getName());
        WalletResponse response = walletService.getBalance(authentication);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Initiate top-up", description = "Initiate a wallet top-up transaction (Rider only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Top-up initiated successfully",
                    content = @Content(schema = @Schema(implementation = TopUpInitResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only riders can top-up")
    })
    @PostMapping("/topup/init")
    @PreAuthorize("hasRole('RIDER')")
    public ResponseEntity<TopUpInitResponse> initiateTopUp(
            @Valid @RequestBody TopUpInitRequest request,
            Authentication authentication) {
        log.info("Top-up init request from user: {}, amount: {}, method: {}",
                authentication.getName(), request.getAmount(), request.getPaymentMethod());
        TopUpInitResponse response = walletService.initiateTopUp(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Initiate payout", description = "Initiate a wallet payout/withdrawal (Driver only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payout initiated successfully",
                    content = @Content(schema = @Schema(implementation = PayoutInitResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient balance"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only drivers can request payouts")
    })
    @PostMapping("/payout/init")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<PayoutInitResponse> initiatePayout(
            @Valid @RequestBody PayoutInitRequest request,
            Authentication authentication) {
        log.info("Payout init request from user: {}, amount: {}, bank: {}",
                authentication.getName(), request.getAmount(), request.getBankName());
        PayoutInitResponse response = walletService.initiatePayout(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get driver earnings", description = "Retrieve driver earnings summary (Driver only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Earnings retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DriverEarningsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only drivers can view earnings")
    })
    @GetMapping("/earnings")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<DriverEarningsResponse> getEarnings(Authentication authentication) {
        log.info("Get earnings request from driver: {}", authentication.getName());
        DriverEarningsResponse response = walletService.getDriverEarnings(authentication);
        return ResponseEntity.ok(response);
    }
}