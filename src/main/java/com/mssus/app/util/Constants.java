package com.mssus.app.util;

public class Constants {
    // OTP Purposes
    public static final String OTP_FORGOT_PASSWORD = "FORGOT_PASSWORD";
    public static final String OTP_VERIFY_EMAIL = "VERIFY_MAIL";
    public static final String OTP_VERIFY_PHONE = "VERIFY_PHONE";

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
