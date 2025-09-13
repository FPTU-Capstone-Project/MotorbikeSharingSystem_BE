//package com.mssus.app.service;
//
//import com.mssus.app.dto.request.LoginRequest;
//import com.mssus.app.dto.request.RegisterRequest;
//import com.mssus.app.dto.response.LoginResponse;
//import com.mssus.app.dto.response.RegisterResponse;
//import com.mssus.app.entity.UserEntity;
//import com.mssus.app.exception.ConflictException;
//import com.mssus.app.exception.UnauthorizedException;
//import com.mssus.app.repository.UserRepository;
//import com.mssus.app.security.JwtService;
//import com.mssus.app.service.impl.AuthServiceImpl;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.crypto.password.PasswordEncoder;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class AuthServiceTest {
//
//    @Mock
//    private UserRepository userRepository;
//
//    @Mock
//    private PasswordEncoder passwordEncoder;
//
//    @Mock
//    private JwtService jwtService;
//
//    @Mock
//    private AuthenticationManager authenticationManager;
//
//    @InjectMocks
//    private AuthServiceImpl authService;
//
//    private RegisterRequest registerRequest;
//    private LoginRequest loginRequest;
//    private UserEntity userEntity;
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
//
//        userEntity = UserEntity.builder()
//                .userId(1)
//                .email("test@example.com")
//                .phone("0901234567")
//                .fullName("Test User")
//                .passwordHash("hashedPassword")
//                .userType("student")
//                .isActive(true)
//                .emailVerified(false)
//                .phoneVerified(false)
//                .createdAt(LocalDateTime.now())
//                .build();
//    }
//
//    @Test
//    void register_Success() {
//        // Arrange
//        when(userRepository.existsByEmail(anyString())).thenReturn(false);
//        when(userRepository.existsByPhone(anyString())).thenReturn(false);
//        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
//        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
//        when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("jwt-token");
//
//        // Act
//        RegisterResponse response = authService.register(registerRequest);
//
//        // Assert
//        assertNotNull(response);
//        assertEquals("test@example.com", response.getEmail());
//        assertEquals("Test User", response.getFullName());
//        assertEquals("jwt-token", response.getToken());
//        verify(userRepository).save(any(UserEntity.class));
//    }
//
//    @Test
//    void register_EmailAlreadyExists_ThrowsConflictException() {
//        // Arrange
//        when(userRepository.existsByEmail(anyString())).thenReturn(true);
//
//        // Act & Assert
//        assertThrows(ConflictException.class, () -> authService.register(registerRequest));
//        verify(userRepository, never()).save(any(UserEntity.class));
//    }
//
//    @Test
//    void register_PhoneAlreadyExists_ThrowsConflictException() {
//        // Arrange
//        when(userRepository.existsByEmail(anyString())).thenReturn(false);
//        when(userRepository.existsByPhone(anyString())).thenReturn(true);
//
//        // Act & Assert
//        assertThrows(ConflictException.class, () -> authService.register(registerRequest));
//        verify(userRepository, never()).save(any(UserEntity.class));
//    }
//
//    @Test
//    void login_Success_WithEmail() {
//        // Arrange
//        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(userEntity));
//        when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("access-token");
//        when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");
//        when(jwtService.getExpirationTime()).thenReturn(3600000L);
//
//        // Act
//        LoginResponse response = authService.login(loginRequest);
//
//        // Assert
//        assertNotNull(response);
//        assertEquals(1, response.getUserId());
//        assertEquals("access-token", response.getToken());
//        assertEquals("refresh-token", response.getRefreshToken());
//        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
//    }
//
//    @Test
//    void login_Success_WithPhone() {
//        // Arrange
//        loginRequest.setEmailOrPhone("0901234567");
//        when(userRepository.findByPhone(anyString())).thenReturn(Optional.of(userEntity));
//        when(jwtService.generateToken(anyString(), any(Map.class))).thenReturn("access-token");
//        when(jwtService.generateRefreshToken(anyString())).thenReturn("refresh-token");
//        when(jwtService.getExpirationTime()).thenReturn(3600000L);
//
//        // Act
//        LoginResponse response = authService.login(loginRequest);
//
//        // Assert
//        assertNotNull(response);
//        assertEquals(1, response.getUserId());
//        assertEquals("access-token", response.getToken());
//        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
//    }
//
//    @Test
//    void login_UserNotFound_ThrowsUnauthorizedException() {
//        // Arrange
//        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
//
//        // Act & Assert
//        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
//        verify(authenticationManager, never()).authenticate(any());
//    }
//
//    @Test
//    void login_UserInactive_ThrowsUnauthorizedException() {
//        // Arrange
//        userEntity.setIsActive(false);
//        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(userEntity));
//
//        // Act & Assert
//        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
//        verify(authenticationManager, never()).authenticate(any());
//    }
//}
