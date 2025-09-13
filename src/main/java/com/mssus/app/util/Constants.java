package com.mssus.app.util;

public class Constants {
    
    // User Types
    public static final String USER_TYPE_STUDENT = "student";
    public static final String USER_TYPE_ADMIN = "admin";
    
    // Profile Status
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_SUSPENDED = "suspended";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_EXPIRED = "expired";
    
    // Verification Types
    public static final String VERIFICATION_STUDENT_ID = "student_id";
    public static final String VERIFICATION_DRIVER_LICENSE = "driver_license";
    public static final String VERIFICATION_BACKGROUND_CHECK = "background_check";
    public static final String VERIFICATION_VEHICLE_REGISTRATION = "vehicle_registration";
    
    // OTP Purposes
    public static final String OTP_FORGOT_PASSWORD = "FORGOT_PASSWORD";
    public static final String OTP_VERIFY_EMAIL = "VERIFY_MAIL";
    public static final String OTP_VERIFY_PHONE = "VERIFY_PHONE";
    
    // Payment Methods
    public static final String PAYMENT_METHOD_WALLET = "wallet";
    public static final String PAYMENT_METHOD_CREDIT_CARD = "credit_card";
    
    // File Types
    public static final String FILE_TYPE_IMAGE = "image";
    public static final String FILE_TYPE_PDF = "pdf";
    
    // Default Values
    public static final Float DEFAULT_RATING = 5.0f;
    public static final Integer DEFAULT_MAX_PASSENGERS = 1;
    public static final String DEFAULT_COMMISSION_RATE = "0.15";
    
    // File Size Limits (in bytes)
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    
    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    
    private Constants() {
        // Private constructor to prevent instantiation
    }
}
