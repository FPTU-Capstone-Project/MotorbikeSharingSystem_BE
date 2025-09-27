package com.mssus.app.controller;

import com.mssus.app.dto.response.notification.EmailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email/debug")
@RequiredArgsConstructor
@Slf4j
public class EmailDebugController {

    @GetMapping("/test-result")
    public ResponseEntity<String> testEmailResult() {
        try {
            // Test EmailResult creation
            EmailResult successResult = EmailResult.success("test-message-id");
            EmailResult failureResult = EmailResult.failure("test error message");

            log.info("Success result: {}", successResult);
            log.info("Failure result: {}", failureResult);

            String response = String.format(
                "EmailResult test successful!\n" +
                "Success: %s\n" +
                "Failure: %s",
                successResult,
                failureResult
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing EmailResult", e);
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/test-simple")
    public ResponseEntity<EmailResult> testSimple() {
        try {
            EmailResult result = EmailResult.success("Simple test successful");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in simple test", e);
            EmailResult errorResult = EmailResult.failure("Error: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResult);
        }
    }
}