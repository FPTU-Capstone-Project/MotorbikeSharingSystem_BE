package com.mssus.app.config;

import com.mssus.app.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initialize bank list on application startup if banks.json doesn't exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // Run early in startup
public class BankInitializer implements CommandLineRunner {

    private final BankService bankService;

    @Override
    public void run(String... args) {
        try {
            log.info("Checking bank list initialization...");
            
            // Try to load banks
            var banks = bankService.loadBanks();
            
            if (banks.isEmpty()) {
                log.info("banks.json not found or empty, fetching from VietQR API...");
                bankService.fetchAndSaveBanks();
                log.info("Bank list initialized successfully");
            } else {
                log.info("Bank list loaded: {} banks found", banks.size());
            }
        } catch (Exception e) {
            log.error("Failed to initialize bank list on startup", e);
            // Don't fail startup - will retry on next scheduled update
        }
    }
}

