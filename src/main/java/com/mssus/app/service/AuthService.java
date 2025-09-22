package com.mssus.app.service;

import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    
    RegisterResponse register(RegisterRequest request);
    
    LoginResponse login(LoginRequest request);
    
    MessageResponse logout(String token);
    MessageResponse forgotPassword(ForgotPasswordRequest request);

    OtpResponse requestOtp(String username, String otpFor);

    OtpResponse verifyOtp(OtpRequest request);
}
