package com.mssus.app.controller;

import com.mssus.app.service.PayOSService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/payos")
@RequiredArgsConstructor
@Slf4j
public class PayOSController {
    private final PayOSService payOSService;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Top-up payment link created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/create-topup-link")
    public ResponseEntity<CheckoutResponseData> createTopUpPaymentLink(
            @RequestParam Integer userId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "Wallet Top-up") String description) throws Exception {

        CheckoutResponseData response = payOSService.createTopUpPaymentLink(userId, amount, description);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload) {
        try {
            log.info("Received PayOS webhook");
            payOSService.handleWebhook(payload);
            return ResponseEntity.ok("Webhook processed successfully");
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.status(500).body("Webhook processing failed");
        }
    }
}
