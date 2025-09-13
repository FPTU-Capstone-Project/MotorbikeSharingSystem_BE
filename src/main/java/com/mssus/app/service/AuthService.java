package com.mssus.app.service;

import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    
    RegisterResponse register(RegisterRequest request);
    
    LoginResponse login(LoginRequest request);
    
    MessageResponse logout(String token);
    
    UserProfileResponse getCurrentUserProfile(String username);
    
    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);
    
    MessageResponse updatePassword(String username, UpdatePasswordRequest request);
    
    MessageResponse forgotPassword(ForgotPasswordRequest request);
    
    OtpResponse requestOtp(String username, String otpFor);
    
    OtpResponse verifyOtp(OtpRequest request);
    
    MessageResponse updateAvatar(String username, MultipartFile file);
    
    MessageResponse switchProfile(String username, SwitchProfileRequest request);
    
    VerificationResponse submitStudentVerification(String username, MultipartFile document);
    
    VerificationResponse submitDriverVerification(String username, DriverVerificationRequest request);
}
