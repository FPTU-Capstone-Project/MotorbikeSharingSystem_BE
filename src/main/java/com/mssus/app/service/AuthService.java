package com.mssus.app.service;

import com.mssus.app.common.enums.OtpFor;
import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    
    RegisterResponse register(RegisterRequest request);
    
    LoginResponse login(LoginRequest request);
    
    MessageResponse logout(String refreshToken);

    TokenRefreshResponse refreshToken(TokenRefreshRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);
}
