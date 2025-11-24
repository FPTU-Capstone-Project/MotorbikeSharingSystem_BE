package com.mssus.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.dto.request.wallet.PayoutWebhookRequest;
import com.mssus.app.dto.request.wallet.PayOSPayoutListRequest;
import com.mssus.app.service.PayOSService;
import com.mssus.app.service.PayoutWebhookService;
import com.mssus.app.service.impl.PayOSPayoutClient;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOSController {

    private final PayOSService payOSService;
    private final PayoutWebhookService payoutWebhookService;
    private final PayOSPayoutClient payOSPayoutClient;
    private final ObjectMapper objectMapper;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Top-up payment link created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/create-topup-link")
    public ResponseEntity<CheckoutResponseData> createTopUpPaymentLink(
            @RequestParam Integer userId,
            @RequestParam BigDecimal amount,
            @RequestParam String email,
            @RequestParam(defaultValue = "Wallet Top-up") String description,
            @RequestParam String returnUrl,
            @RequestParam String cancelUrl
    ) throws Exception {
        CheckoutResponseData response = payOSService.createTopUpPaymentLink(
                userId,
                amount,
                email,
                description,
                returnUrl,
                cancelUrl
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload) {
        try {
            log.info("Received PayOS webhook");
//            PayOSService.WebhookPayload webhookPayload = payOSService.parseWebhook(payload);
//            topUpService.handleTopUpWebhook(
//                    webhookPayload.orderCode(),
//                    webhookPayload.status(),
//                    webhookPayload.amount()
//            );
            return ResponseEntity.ok("Succeeded");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.internalServerError().body("Failed");
        }
    }

    @PutMapping("/confirm-webhook")
    public ResponseEntity<String> confirmWebhook(
            @RequestBody String webhookUrl
    ) {
        return ResponseEntity.ok(payOSService.confirmWebhook(webhookUrl));
    }

    /**
     * Handle PayOS payout webhook callback.
     * Verifies signature and updates transaction status.
     */
    @PostMapping("/payout/webhook")
    public ResponseEntity<String> handlePayoutWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "x-signature", required = false) String signature) {
        try {
            log.info("Received PayOS payout webhook");
            
            // Parse webhook payload
            PayoutWebhookRequest webhookRequest = objectMapper.readValue(rawPayload, PayoutWebhookRequest.class);
            
            // Set signature from header if not in body
            if (webhookRequest.getSignature() == null && signature != null) {
                webhookRequest.setSignature(signature);
            }
            
            // Process webhook
            payoutWebhookService.handlePayoutWebhook(webhookRequest, rawPayload);
            
            return ResponseEntity.ok("Succeeded");
        } catch (Exception e) {
            log.error("Error processing payout webhook", e);
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }

    @GetMapping("/payouts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> listPayoutOrders(@Valid @ModelAttribute PayOSPayoutListRequest request) {
        JsonNode response = payOSPayoutClient.listPayoutOrders(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payouts/{payoutId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> getPayoutOrder(@PathVariable String payoutId) {
        JsonNode response = payOSPayoutClient.getPayoutOrder(payoutId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payouts-account/balance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<JsonNode> getPayoutAccountBalance() {
        JsonNode response = payOSPayoutClient.getPayoutAccountBalance();
        return ResponseEntity.ok(response);
    }
}

