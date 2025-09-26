package com.mssus.app.common.exception.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for loading and managing the error catalog.
 * Provides methods to retrieve error entries by ID and handles catalog initialization.
 */
@Slf4j
@Service
public class ErrorCatalogService {
    
    private static final String ERROR_CATALOG_FILE = "errors.yaml";
    private static final String DEFAULT_ERROR_ID = "system.internal.unexpected";
    
    private final Map<String, ErrorEntry> errorCatalog = new ConcurrentHashMap<>();
    private ErrorEntry defaultErrorEntry;
    
    /**
     * Initialize the error catalog by loading from YAML file
     */
    @PostConstruct
    public void initCatalog() {
        try {
            loadErrorCatalog();
            log.info("Error catalog initialized with {} entries", errorCatalog.size());
        } catch (Exception e) {
            log.error("Failed to load error catalog", e);
            // Create a fallback default error entry
            createFallbackDefaultEntry();
        }
    }
    
    /**
     * Get an error entry by its ID
     * 
     * @param errorId The error ID to look up
     * @return ErrorEntry if found, default error entry otherwise
     */
    public ErrorEntry getErrorEntry(String errorId) {
        if (errorId == null || errorId.trim().isEmpty()) {
            log.warn("Null or empty error ID provided, returning default error");
            return getDefaultErrorEntry();
        }
        
        ErrorEntry entry = errorCatalog.get(errorId);
        if (entry == null) {
            log.warn("Error ID '{}' not found in catalog, returning default error", errorId);
            return getDefaultErrorEntry();
        }
        
        return entry;
    }
    
    /**
     * Get error entry with optional fallback message
     * 
     * @param errorId The error ID to look up
     * @param fallbackMessage Message to use if error not found in catalog
     * @return ErrorEntry with either catalog message or fallback message
     */
    public ErrorEntry getErrorEntry(String errorId, String fallbackMessage) {
        ErrorEntry entry = errorCatalog.get(errorId);
        if (entry == null) {
            log.warn("Error ID '{}' not found in catalog, using fallback message", errorId);
            ErrorEntry fallbackEntry = getDefaultErrorEntry();
            // Create a copy with the fallback message
            return ErrorEntry.builder()
                    .id(errorId)
                    .httpStatus(fallbackEntry.getHttpStatus())
                    .severity(fallbackEntry.getSeverity())
                    .isRetryable(fallbackEntry.getIsRetryable())
                    .messageTemplate(fallbackMessage != null ? fallbackMessage : fallbackEntry.getMessageTemplate())
                    .domain(fallbackEntry.getDomain())
                    .category(fallbackEntry.getCategory())
                    .owner(fallbackEntry.getOwner())
                    .build();
        }
        
        return entry;
    }
    
    /**
     * Check if an error ID exists in the catalog
     * 
     * @param errorId The error ID to check
     * @return true if the error ID exists in the catalog
     */
    public boolean hasErrorEntry(String errorId) {
        return errorId != null && errorCatalog.containsKey(errorId);
    }
    
    /**
     * Get all error entries in the catalog
     * 
     * @return Map of error ID to ErrorEntry
     */
    public Map<String, ErrorEntry> getAllErrorEntries() {
        return Map.copyOf(errorCatalog);
    }
    
    /**
     * Get the default error entry used when specific errors are not found
     * 
     * @return Default ErrorEntry
     */
    public ErrorEntry getDefaultErrorEntry() {
        return defaultErrorEntry != null ? defaultErrorEntry : createFallbackDefaultEntry();
    }
    
    /**
     * Load the error catalog from YAML file
     */
    @SuppressWarnings("unchecked")
    private void loadErrorCatalog() throws IOException {
        ClassPathResource resource = new ClassPathResource(ERROR_CATALOG_FILE);
        
        if (!resource.exists()) {
            throw new IOException("Error catalog file not found: " + ERROR_CATALOG_FILE);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);
            
            if (data == null || !data.containsKey("errors")) {
                throw new IOException("Invalid error catalog format: missing 'errors' key");
            }
            
            List<Map<String, Object>> errors = (List<Map<String, Object>>) data.get("errors");
            
            for (Map<String, Object> errorData : errors) {
                ErrorEntry entry = mapToErrorEntry(errorData);
                errorCatalog.put(entry.getId(), entry);
                
                // Set default error entry if this is the default error ID
                if (DEFAULT_ERROR_ID.equals(entry.getId())) {
                    defaultErrorEntry = entry;
                }
            }
            
            // Ensure we have a default error entry
            if (defaultErrorEntry == null) {
                Optional<ErrorEntry> systemError = errorCatalog.values().stream()
                        .filter(entry -> entry.getDomain().equals("system"))
                        .findFirst();
                defaultErrorEntry = systemError.orElseGet(this::createFallbackDefaultEntry);
            }
        }
    }
    
    /**
     * Map YAML data to ErrorEntry object
     */
    private ErrorEntry mapToErrorEntry(Map<String, Object> errorData) {
        return ErrorEntry.builder()
                .id((String) errorData.get("id"))
                .httpStatus((Integer) errorData.get("httpStatus"))
                .severity((String) errorData.getOrDefault("severity", "ERROR"))
                .isRetryable((Boolean) errorData.getOrDefault("isRetryable", false))
                .messageTemplate((String) errorData.get("messageTemplate"))
                .domain((String) errorData.get("domain"))
                .category((String) errorData.get("category"))
                .owner((String) errorData.get("owner"))
                .remediation((String) errorData.get("remediation"))
                .build();
    }
    
    /**
     * Create a fallback default error entry when catalog loading fails
     */
    private ErrorEntry createFallbackDefaultEntry() {
        log.warn("Creating fallback default error entry");
        defaultErrorEntry = ErrorEntry.builder()
                .id(DEFAULT_ERROR_ID)
                .httpStatus(500)
                .severity("ERROR")
                .isRetryable(false)
                .messageTemplate("An unexpected error occurred")
                .domain("system")
                .category("internal")
                .owner("platform-team")
                .build();
        return defaultErrorEntry;
    }
}
