package com.mssus.app.service;

import com.mssus.app.dto.response.bank.BankInfo;

import java.util.List;
import java.util.Optional;

/**
 * Service to manage bank information from VietQR API.
 */
public interface BankService {
    /**
     * Fetch banks from VietQR API and save to resources/banks.json
     */
    void fetchAndSaveBanks();

    /**
     * Load banks from resources/banks.json
     */
    List<BankInfo> loadBanks();

    /**
     * Validate bank BIN
     */
    boolean isValidBankBin(String bankBin);

    /**
     * Get bank info by BIN
     */
    Optional<BankInfo> getBankByBin(String bankBin);

    /**
     * Get all supported banks (transferSupported = 1)
     */
    List<BankInfo> getSupportedBanks();
}

