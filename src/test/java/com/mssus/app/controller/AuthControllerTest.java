//package com.mssus.app.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.mssus.app.dto.request.LoginRequest;
//import com.mssus.app.dto.request.RegisterRequest;
//import com.mssus.app.dto.response.LoginResponse;
//import com.mssus.app.dto.response.RegisterResponse;
//import com.mssus.app.exception.ConflictException;
//import com.mssus.app.exception.UnauthorizedException;
//import com.mssus.app.service.AuthService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.time.LocalDateTime;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(AuthController.class)
//class AuthControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean
//    private AuthService authService;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    private RegisterRequest registerRequest;
//    private LoginRequest loginRequest;
//
//    @BeforeEach
//    void setUp() {
//        registerRequest = RegisterRequest.builder()
//                .fullName("Test User")
//                .email("test@example.com")
//                .phone("0901234567")
//                .password("TestPass123")
//                .role("rider")
//                .build();
//
//        loginRequest = LoginRequest.builder()
//                .emailOrPhone("test@example.com")
//                .password("TestPass123")
//                .build();
//    }
//
//    @Test
//    void register_Success() throws Exception {
//        // Arrange
//        RegisterResponse response = RegisterResponse.builder()
//                .userId(1)
//                .userType("rider")
//                .email("test@example.com")
//                .phone("0901234567")
//                .fullName("Test User")
//                .token("jwt-token")
//                .createdAt(LocalDateTime.now())
//                .build();
//
//        when(authService.register(any(RegisterRequest.class))).thenReturn(response);
//
//        // Act & Assert
//        mockMvc.perform(post("/api/v1/auth/register")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(registerRequest)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.email").value("test@example.com"))
//                .andExpect(jsonPath("$.full_name").value("Test User"))
//                .andExpected(jsonPath("$.token").value("jwt-token"));
//    }
//
//    @Test
//    void register_ValidationError() throws Exception {
//        // Arrange - Invalid email
//        registerRequest.setEmail("invalid-email");
//
//        // Act & Assert
//        mockMvc.perform(post("/api/v1/auth/register")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(registerRequest)))
//                .andExpect(status().isBadRequest())
//                .andExpected(jsonPath("$.error").value("VALIDATION_ERROR"));
//    }
//
//    @Test
//    void register_EmailConflict() throws Exception {
//        // Arrange
//        when(authService.register(any(RegisterRequest.class)))
//                .thenThrow(ConflictException.emailAlreadyExists("test@example.com"));
//
//        // Act & Assert
//        mockMvc.perform(post("/api/v1/auth/register")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(registerRequest)))
//                .andExpect(status().isConflict())
//                .andExpected(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"));
//    }
//
//    @Test
//    void login_Success() throws Exception {
//        // Arrange
//        LoginResponse response = LoginResponse.builder()
//                .userId(1)
//                .userType("rider")
//                .token("access-token")
//                .refreshToken("refresh-token")
//                .expiresIn(3600L)
//                .build();
//
//        when(authService.login(any(LoginRequest.class))).thenReturn(response);
//
//        // Act & Assert
//        mockMvc.perform(post("/api/v1/auth/login")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(loginRequest)))
//                .andExpect(status().isOk())
//                .andExpected(jsonPath("$.user_id").value(1))
//                .andExpected(jsonPath("$.token").value("access-token"));
//    }
//
//    @Test
//    void login_InvalidCredentials() throws Exception {
//        // Arrange
//        when(authService.login(any(LoginRequest.class)))
//                .thenThrow(UnauthorizedException.invalidCredentials());
//
//        // Act & Assert
//        mockMvc.perform(post("/api/v1/auth/login")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(loginRequest)))
//                .andExpect(status().isUnauthorized())
//                .andExpected(jsonPath("$.error").value("INVALID_CREDENTIALS"));
//    }
//
//    @Test
//    void login_MissingRequiredFields() throws Exception {
//        // Arrange - Empty request
//        LoginRequest emptyRequest = new LoginRequest();
//
//        // Act & Assert
//        mockMvc.perform(post("/api/v1/auth/login")
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(emptyRequest)))
//                .andExpect(status().isBadRequest())
//                .andExpected(jsonPath("$.error").value("VALIDATION_ERROR"));
//    }
//}
