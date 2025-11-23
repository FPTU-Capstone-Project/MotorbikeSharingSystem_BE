package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.dto.response.bank.BankInfo;
import com.mssus.app.dto.response.bank.VietQRBankResponse;
import com.mssus.app.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankServiceImpl implements BankService {

    private static final String BANKS_JSON_PATH = "classpath:banks.json";
    private static final String VIETQR_API_URL = "https://api.vietqr.io/v2/banks";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final WebClient.Builder webClientBuilder;

    @Value("${app.banks.file-path:banks.json}")
    private String banksFilePath;

    private WebClient getWebClient() {
        return webClientBuilder.build();
    }

    @Override
    public void fetchAndSaveBanks() {
        try {
            log.info("Fetching banks from VietQR API: {}", VIETQR_API_URL);

            // Fetch from API
            String responseBody = getWebClient().get()
                    .uri(VIETQR_API_URL)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (responseBody == null || responseBody.isEmpty()) {
                log.error("Empty response from VietQR API");
                return;
            }

            // Parse response
            VietQRBankResponse response = objectMapper.readValue(responseBody, VietQRBankResponse.class);

            if (!"00".equals(response.getCode())) {
                log.error("VietQR API returned error: code={}, desc={}", response.getCode(), response.getDesc());
                return;
            }

            List<BankInfo> banks = response.getData();
            if (banks == null || banks.isEmpty()) {
                log.warn("No banks found in VietQR API response");
                return;
            }

            log.info("Fetched {} banks from VietQR API", banks.size());

            // Sort banks by id ascending
            List<BankInfo> sortedBanks = banks.stream()
                    .sorted((b1, b2) -> {
                        Integer id1 = b1.getId() != null ? b1.getId() : Integer.MAX_VALUE;
                        Integer id2 = b2.getId() != null ? b2.getId() : Integer.MAX_VALUE;
                        return id1.compareTo(id2);
                    })
                    .collect(java.util.stream.Collectors.toList());

            // Save to resources/banks.json
            saveBanksToFile(sortedBanks);

            log.info("Successfully saved {} banks to {}", banks.size(), banksFilePath);

        } catch (Exception e) {
            log.error("Failed to fetch and save banks from VietQR API", e);
            throw new RuntimeException("Failed to fetch banks: " + e.getMessage(), e);
        }
    }

    private void saveBanksToFile(List<BankInfo> banks) throws IOException {
        try {
            // Try to save to resources directory (development)
            Resource resource = resourceLoader.getResource("classpath:");
            String resourcesPath = resource.getURI().getPath();

            if (!resourcesPath.contains(".jar!")) {
                // Running from IDE/development - save to resources directory
                Path resourcesDir = Paths.get(resourcesPath);
                Path banksFile = resourcesDir.resolve(banksFilePath);
                Files.createDirectories(banksFile.getParent());
                try (FileWriter writer = new FileWriter(banksFile.toFile())) {
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, banks);
                }
                log.info("Saved banks to resources directory: {}", banksFile);
                return;
            }
        } catch (Exception e) {
            log.debug("Cannot save to resources directory (likely running from JAR): {}", e.getMessage());
        }

        // Running from JAR or resources directory not writable - save to current working directory
        Path path = Paths.get(banksFilePath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (FileWriter writer = new FileWriter(path.toFile())) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, banks);
        }
        log.info("Saved banks to working directory: {}", path.toAbsolutePath());
    }

    @Override
    public List<BankInfo> loadBanks() {
        try {
            String jsonContent = null;
            Resource resource = null;
            
            // Try to load from classpath first
            resource = resourceLoader.getResource(BANKS_JSON_PATH);
            if (resource.exists()) {
                jsonContent = new String(resource.getInputStream().readAllBytes());
            } else {
                // Try to load from working directory (for JAR deployments)
                Path workingDirFile = Paths.get(banksFilePath);
                if (Files.exists(workingDirFile)) {
                    jsonContent = Files.readString(workingDirFile);
                }
            }

            if (jsonContent == null || jsonContent.trim().isEmpty()) {
                log.warn("banks.json not found in classpath or working directory");
                return List.of();
            }

            // Try to parse as VietQRBankResponse first (wrapped format)
            try {
                VietQRBankResponse response = objectMapper.readValue(jsonContent, VietQRBankResponse.class);
                if (response.getData() != null && !response.getData().isEmpty()) {
                    log.debug("Loaded {} banks from wrapped response format", response.getData().size());
                    return response.getData();
                }
            } catch (Exception e) {
                log.debug("Not a wrapped response format, trying direct array format");
            }

            // Try to parse as direct array format
            try {
                List<BankInfo> banks = objectMapper.readValue(
                        jsonContent,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, BankInfo.class));
                log.debug("Loaded {} banks from direct array format", banks.size());
                return banks;
            } catch (Exception e) {
                log.error("Failed to parse banks.json - invalid format", e);
                return List.of();
            }

        } catch (Exception e) {
            log.error("Failed to load banks from file", e);
            return List.of();
        }
    }

    @Override
    public boolean isValidBankBin(String bankBin) {
        if (bankBin == null || bankBin.trim().isEmpty()) {
            return false;
        }

        // Validate format: 6 digits
        if (!bankBin.matches("^\\d{6}$")) {
            return false;
        }

        // Check if exists in banks list
        List<BankInfo> banks = loadBanks();
        return banks.stream()
                .anyMatch(bank -> bankBin.equals(bank.getBin()));
    }

    @Override
    public Optional<BankInfo> getBankByBin(String bankBin) {
        if (bankBin == null || bankBin.trim().isEmpty()) {
            return Optional.empty();
        }

        List<BankInfo> banks = loadBanks();
        return banks.stream()
                .filter(bank -> bankBin.equals(bank.getBin()))
                .findFirst();
    }

    @Override
    public List<BankInfo> getSupportedBanks() {
        List<BankInfo> banks = loadBanks();
        return banks.stream()
                .filter(bank -> bank.getTransferSupported() != null && bank.getTransferSupported() == 1)
                .collect(Collectors.toList());
    }
}

