package com.mssus.app.controller;

import com.mssus.app.dto.response.bank.BankInfo;
import com.mssus.app.service.BankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
@Tag(name = "Banks", description = "Bank information and validation operations")
public class BankController {

    private final BankService bankService;

    @Operation(
            summary = "Get all banks",
            description = "Retrieve list of all banks from VietQR API"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Banks retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BankInfo.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<BankInfo>> getAllBanks() {
        log.debug("Get all banks request");
        List<BankInfo> banks = bankService.loadBanks();
        return ResponseEntity.ok(banks);
    }

    @Operation(
            summary = "Get supported banks",
            description = "Retrieve list of banks that support transfers (transferSupported = 1)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Supported banks retrieved successfully",
                    content = @Content(schema = @Schema(implementation = BankInfo.class))
            )
    })
    @GetMapping("/supported")
    public ResponseEntity<List<BankInfo>> getSupportedBanks() {
        log.debug("Get supported banks request");
        List<BankInfo> banks = bankService.getSupportedBanks();
        return ResponseEntity.ok(banks);
    }

    @Operation(
            summary = "Get bank by BIN",
            description = "Retrieve bank information by BIN (6-digit bank code)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bank found",
                    content = @Content(schema = @Schema(implementation = BankInfo.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Bank not found"
            )
    })
    @GetMapping("/{bin}")
    public ResponseEntity<BankInfo> getBankByBin(
            @Parameter(description = "6-digit bank BIN code", example = "970415", required = true)
            @PathVariable String bin) {
        log.debug("Get bank by BIN request: {}", bin);
        
        Optional<BankInfo> bank = bankService.getBankByBin(bin);
        
        if (bank.isPresent()) {
            return ResponseEntity.ok(bank.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Validate bank BIN",
            description = "Check if a bank BIN is valid and exists in the bank list"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Validation result"
            )
    })
    @GetMapping("/validate/{bin}")
    public ResponseEntity<Map<String, Object>> validateBankBin(
            @Parameter(description = "6-digit bank BIN code", example = "970415", required = true)
            @PathVariable String bin) {
        log.debug("Validate bank BIN request: {}", bin);
        
        boolean isValid = bankService.isValidBankBin(bin);
        Optional<BankInfo> bank = bankService.getBankByBin(bin);
        
        Map<String, Object> response = new HashMap<>();
        response.put("bin", bin);
        response.put("valid", isValid);
        
        if (bank.isPresent()) {
            response.put("bank", bank.get());
            response.put("transferSupported", bank.get().getTransferSupported() != null && bank.get().getTransferSupported() == 1);
        }
        
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get bank list statistics",
            description = "Get statistics about the bank list (total, supported, etc.)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Statistics retrieved successfully"
            )
    })
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBankStats() {
        log.debug("Get bank statistics request");
        
        List<BankInfo> allBanks = bankService.loadBanks();
        List<BankInfo> supportedBanks = bankService.getSupportedBanks();
        
        long transferSupportedCount = supportedBanks.size();
        
        long lookupSupportedCount = allBanks.stream()
                .filter(bank -> bank.getLookupSupported() != null && bank.getLookupSupported() == 1)
                .count();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBanks", allBanks.size());
        stats.put("transferSupported", transferSupportedCount);
        stats.put("lookupSupported", lookupSupportedCount);
        stats.put("lastUpdated", getLastUpdatedTimestamp());
        
        return ResponseEntity.ok(stats);
    }

    private String getLastUpdatedTimestamp() {
        // Try to get file modification time
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("banks.json");
            if (java.nio.file.Files.exists(path)) {
                java.nio.file.attribute.FileTime fileTime = java.nio.file.Files.getLastModifiedTime(path);
                return fileTime.toInstant().toString();
            }
        } catch (Exception e) {
            log.debug("Cannot get file modification time: {}", e.getMessage());
        }
        return "Unknown";
    }
}

