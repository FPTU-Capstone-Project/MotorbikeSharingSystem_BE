package com.mssus.app.common.util;

import java.util.regex.Pattern;

public class ValidationUtil {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^(\\+84|0)[0-9]{9,10}$"
    );
    
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$"
    );
    
    private static final Pattern PLATE_NUMBER_PATTERN = Pattern.compile(
        "^[0-9]{2}[A-Z]{1,2}[-\\s]?[0-9]{4,5}(\\.[0-9]{2})?$"
    );
    
    // Pattern to detect HTML tags and script tags for XSS prevention
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
        "<[^>]*>|javascript:|onerror=|onload=|onclick=", 
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern for valid full name (letters, spaces, common Vietnamese characters, hyphens)
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile(
        "^[a-zA-ZàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđĐ\\s'-]+$"
    );
    
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
    
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }
    
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
    
    public static boolean isValidPlateNumber(String plateNumber) {
        return plateNumber != null && PLATE_NUMBER_PATTERN.matcher(plateNumber).matches();
    }
    
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        
        // Remove spaces and dashes
        phone = phone.replaceAll("[\\s-]", "");
        
        // Convert +84 to 0
        if (phone.startsWith("+84")) {
            phone = "0" + phone.substring(3);
        }
        
        return phone;
    }
    
    public static boolean isEmailOrPhone(String input) {
        return isValidEmail(input) || isValidPhone(input);
    }
    
    /**
     * Check if text contains potentially dangerous HTML/script tags
     * @param text Text to check
     * @return true if text contains HTML/script tags
     */
    public static boolean containsHtmlOrScript(String text) {
        return text != null && HTML_TAG_PATTERN.matcher(text).find();
    }
    
    /**
     * Sanitize text by removing HTML tags and dangerous characters
     * @param text Text to sanitize
     * @return Sanitized text
     */
    public static String sanitizeText(String text) {
        if (text == null) return null;
        
        // Remove HTML tags and dangerous patterns
        String sanitized = text.replaceAll("<[^>]*>", "")
                              .replaceAll("javascript:", "")
                              .replaceAll("onerror=", "")
                              .replaceAll("onload=", "")
                              .replaceAll("onclick=", "")
                              .replaceAll("on\\w+\\s*=", "")  // Remove all event handlers
                              .trim();
        
        return sanitized;
    }
    
    /**
     * Validate full name format and check for XSS attacks
     * @param fullName Full name to validate
     * @return true if full name is valid and safe
     */
    public static boolean isValidFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = fullName.trim();
        
        // Check length
        if (trimmed.length() < 2 || trimmed.length() > 100) {
            return false;
        }
        
        // Check for HTML/script tags (XSS prevention)
        if (containsHtmlOrScript(trimmed)) {
            return false;
        }
        
        // Check if it matches valid name pattern
        return FULL_NAME_PATTERN.matcher(trimmed).matches();
    }
}
