package com.mssus.app.util;

import com.mssus.app.common.enums.OtpFor;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OtpUtil {
    
    private static final SecureRandom random = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 5;
    
    // Simple in-memory OTP storage (replace with Redis in production)
    private static final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    
    public static String generateOtp() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
    
    public static void storeOtp(String key, String otp, OtpFor purpose) {
        OtpData data = new OtpData(otp, purpose, LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpStore.put(key, data);
    }
    
    public static boolean validateOtp(String key, String otp, OtpFor purpose) {
        OtpData data = otpStore.get(key);
        if (data == null) {
            return false;
        }
        
        boolean valid = data.otp.equals(otp) && 
                       data.purpose.equals(purpose) && 
                       LocalDateTime.now().isBefore(data.expiryTime);
        
        if (valid) {
            otpStore.remove(key); // Remove OTP after successful validation
        }
        
        return valid;
    }

    public static void removeOtp(String key) {
        otpStore.remove(key);
    }
    
    public static void clearExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpStore.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiryTime));
    }

    private record OtpData(String otp, OtpFor purpose, LocalDateTime expiryTime) {
    }
}
