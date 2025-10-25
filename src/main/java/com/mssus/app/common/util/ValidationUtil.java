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
}
