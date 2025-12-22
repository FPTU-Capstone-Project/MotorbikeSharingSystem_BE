package com.mssus.app.controller;

import com.mssus.app.dto.request.wallet.PayoutInitRequest;
import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.*;
import com.mssus.app.service.TopUpService;
import com.mssus.app.service.WalletService;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
    private final TopUpService topUpService;
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
        // ✅ Delegate to TopUpService (handles PayOS integration)
        TopUpInitResponse response = topUpService.initiateTopUp(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Initiate payout", description = "Initiate a wallet payout/withdrawal (All authenticated users)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Payout initiated successfully",
                    content = @Content(schema = @Schema(implementation = PayoutInitResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient balance"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/payout/init")
    @PreAuthorize("isAuthenticated()")
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

    // Admin payout processing endpoints

    @Operation(summary = "List pending payouts", description = "Retrieve list of all pending payout requests (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending payouts retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PendingPayoutResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only admins can view pending payouts")
    })
    @GetMapping("/payout/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PendingPayoutResponse>> getPendingPayouts() {
        log.info("Get pending payouts request from admin");
        List<PendingPayoutResponse> response = walletService.getPendingPayouts();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Mark payout as processing", description = "Mark a payout request as processing (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payout marked as processing successfully",
                    content = @Content(schema = @Schema(implementation = PayoutProcessResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only admins can process payouts"),
            @ApiResponse(responseCode = "404", description = "Payout not found")
    })
    @PutMapping("/payout/{payoutRef}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutProcessResponse> processPayout(
            @Parameter(description = "Payout reference ID") @PathVariable String payoutRef,
            Authentication authentication) {
        log.info("Admin {} marking payout {} as processing", authentication.getName(), payoutRef);
        PayoutProcessResponse response = walletService.processPayout(payoutRef, authentication);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Complete payout with evidence", description = "Complete a payout request with transfer evidence (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payout completed successfully",
                    content = @Content(schema = @Schema(implementation = PayoutProcessResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing evidence file"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only admins can complete payouts"),
            @ApiResponse(responseCode = "404", description = "Payout not found")
    })
    @PutMapping(value = "/payout/{payoutRef}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutProcessResponse> completePayout(
            @Parameter(description = "Payout reference ID") @PathVariable String payoutRef,
            @Parameter(description = "Evidence file (screenshot, receipt, transaction ID)") @RequestParam("evidenceFile") MultipartFile evidenceFile,
            @Parameter(description = "Notes or additional information") @RequestParam(value = "notes", required = false) String notes,
            Authentication authentication) {
        log.info("Admin {} completing payout {} with evidence", authentication.getName(), payoutRef);
        PayoutProcessResponse response = walletService.completePayout(payoutRef, evidenceFile, notes, authentication);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fail payout with reason", description = "Fail a payout request with reason (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payout failed successfully",
                    content = @Content(schema = @Schema(implementation = PayoutProcessResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing reason"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Only admins can fail payouts"),
            @ApiResponse(responseCode = "404", description = "Payout not found")
    })
    @PutMapping("/payout/{payoutRef}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayoutProcessResponse> failPayout(
            @Parameter(description = "Payout reference ID") @PathVariable String payoutRef,
            @Parameter(description = "Failure reason") @RequestParam("reason") String reason,
            Authentication authentication) {
        log.info("Admin {} failing payout {} with reason: {}", authentication.getName(), payoutRef, reason);
        PayoutProcessResponse response = walletService.failPayout(payoutRef, reason, authentication);
        return ResponseEntity.ok(response);
    }
}