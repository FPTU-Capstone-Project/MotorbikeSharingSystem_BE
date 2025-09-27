package com.mssus.app.controller;

import com.mssus.app.dto.response.notification.EmailResult;
import com.mssus.app.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/email/test")
@RequiredArgsConstructor
@Slf4j
public class EmailTestController {

    private final EmailService emailService;

    @Operation(summary = "Test payment success email", description = "Send a test payment success email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email sent successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to send email")
    })
    @PostMapping("/payment-success")
    public ResponseEntity<String> testPaymentSuccessEmail(
            @RequestParam String email,
            @RequestParam(defaultValue = "Test User") String fullName,
            @RequestParam(defaultValue = "500000") BigDecimal amount,
            @RequestParam(defaultValue = "TEST123456") String transactionId) {

        try {
            CompletableFuture<EmailResult> result = emailService.sendPaymentSuccessEmail(
                    email, fullName, amount, transactionId);

            EmailResult emailResult = result.get();

            if (emailResult.isSuccess()) {
                return ResponseEntity.ok("Payment success email sent successfully to: " + email);
            } else {
                return ResponseEntity.status(500).body("Failed to send email: " + emailResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error sending test payment success email", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @Operation(summary = "Test top-up success email", description = "Send a test top-up success email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email sent successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to send email")
    })
    @PostMapping("/topup-success")
    public ResponseEntity<String> testTopUpSuccessEmail(
            @RequestParam String email,
            @RequestParam(defaultValue = "Test User") String fullName,
            @RequestParam(defaultValue = "500000") BigDecimal amount,
            @RequestParam(defaultValue = "TEST123456") String transactionId,
            @RequestParam(defaultValue = "1500000") BigDecimal newBalance) {

        try {
            CompletableFuture<EmailResult> result = emailService.sendTopUpSuccessEmail(
                    email, fullName, amount, transactionId, newBalance);

            EmailResult emailResult = result.get();

            if (emailResult.isSuccess()) {
                return ResponseEntity.ok("Top-up success email sent successfully to: " + email);
            } else {
                return ResponseEntity.status(500).body("Failed to send email: " + emailResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error sending test top-up success email", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @Operation(summary = "Test payment failed email", description = "Send a test payment failed email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email sent successfully"),
            @ApiResponse(responseCode = "500", description = "Failed to send email")
    })
    @PostMapping("/payment-failed")
    public ResponseEntity<String> testPaymentFailedEmail(
            @RequestParam String email,
            @RequestParam(defaultValue = "Test User") String fullName,
            @RequestParam(defaultValue = "500000") BigDecimal amount,
            @RequestParam(defaultValue = "TEST123456") String transactionId,
            @RequestParam(defaultValue = "Payment was cancelled by user") String reason) {

        try {
            CompletableFuture<EmailResult> result = emailService.sendPaymentFailedEmail(
                    email, fullName, amount, transactionId, reason);

            EmailResult emailResult = result.get();

            if (emailResult.isSuccess()) {
                return ResponseEntity.ok("Payment failed email sent successfully to: " + email);
            } else {
                return ResponseEntity.status(500).body("Failed to send email: " + emailResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("Error sending test payment failed email", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}