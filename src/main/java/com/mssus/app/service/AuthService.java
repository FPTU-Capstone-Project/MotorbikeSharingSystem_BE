package com.mssus.app.service;

import com.mssus.app.common.enums.OtpFor;
import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AuthService {
    
    RegisterResponse register(RegisterRequest request);
    
    LoginResponse login(LoginRequest request);
    
    MessageResponse logout(String refreshToken);

    TokenRefreshResponse refreshToken(TokenRefreshRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    Map<String, Object> getUserContext(Integer userId);
    
    void setUserContext(Integer userId, Map<String, Object> context);
    
    Map<String, Object> buildTokenClaims(User user, String activeProfile);
    
    void validateUserBeforeGrantingToken(User user);
}
