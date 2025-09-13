//package com.mssus.app.security;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.security.core.userdetails.User;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class JwtServiceTest {
//
//    private JwtService jwtService;
//    private UserDetails userDetails;
//
//    @BeforeEach
//    void setUp() {
//        jwtService = new JwtService();
//
//        // Set test values using reflection
//        ReflectionTestUtils.setField(jwtService, "jwtSecret",
//            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
//        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);
//        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
//
//        userDetails = User.builder()
//                .username("test@example.com")
//                .password("password")
//                .authorities(new ArrayList<>())
//                .build();
//    }
//
//    @Test
//    void generateToken_ValidUsername_ReturnsToken() {
//        // Act
//        String token = jwtService.generateToken("test@example.com");
//
//        // Assert
//        assertNotNull(token);
//        assertFalse(token.isEmpty());
//        assertTrue(token.contains(".")); // JWT tokens contain dots
//    }
//
//    @Test
//    void generateToken_WithExtraClaims_ReturnsToken() {
//        // Arrange
//        Map<String, Object> extraClaims = new HashMap<>();
//        extraClaims.put("userId", 123);
//        extraClaims.put("role", "USER");
//
//        // Act
//        String token = jwtService.generateToken("test@example.com", extraClaims);
//
//        // Assert
//        assertNotNull(token);
//        assertFalse(token.isEmpty());
//    }
//
//    @Test
//    void extractUsername_ValidToken_ReturnsUsername() {
//        // Arrange
//        String token = jwtService.generateToken("test@example.com");
//
//        // Act
//        String username = jwtService.extractUsername(token);
//
//        // Assert
//        assertEquals("test@example.com", username);
//    }
//
//    @Test
//    void validateToken_ValidToken_ReturnsTrue() {
//        // Arrange
//        String token = jwtService.generateToken("test@example.com");
//
//        // Act
//        boolean isValid = jwtService.validateToken(token, userDetails);
//
//        // Assert
//        assertTrue(isValid);
//    }
//
//    @Test
//    void validateToken_InvalidToken_ReturnsFalse() {
//        // Arrange
//        String invalidToken = "invalid.token.here";
//
//        // Act
//        boolean isValid = jwtService.validateToken(invalidToken, userDetails);
//
//        // Assert
//        assertFalse(isValid);
//    }
//
//    @Test
//    void validateToken_DifferentUsername_ReturnsFalse() {
//        // Arrange
//        String token = jwtService.generateToken("other@example.com");
//
//        // Act
//        boolean isValid = jwtService.validateToken(token, userDetails);
//
//        // Assert
//        assertFalse(isValid);
//    }
//
//    @Test
//    void generateRefreshToken_ValidUsername_ReturnsToken() {
//        // Act
//        String refreshToken = jwtService.generateRefreshToken("test@example.com");
//
//        // Assert
//        assertNotNull(refreshToken);
//        assertFalse(refreshToken.isEmpty());
//
//        // Verify it's a valid JWT token
//        String username = jwtService.extractUsername(refreshToken);
//        assertEquals("test@example.com", username);
//    }
//
//    @Test
//    void validateToken_SimpleValidation_ReturnsTrue() {
//        // Arrange
//        String token = jwtService.generateToken("test@example.com");
//
//        // Act
//        boolean isValid = jwtService.validateToken(token);
//
//        // Assert
//        assertTrue(isValid);
//    }
//
//    @Test
//    void validateToken_SimpleValidation_InvalidToken_ReturnsFalse() {
//        // Act
//        boolean isValid = jwtService.validateToken("invalid.token");
//
//        // Assert
//        assertFalse(isValid);
//    }
//
//    @Test
//    void getUsernameFromToken_ValidToken_ReturnsUsername() {
//        // Arrange
//        String token = jwtService.generateToken("test@example.com");
//
//        // Act
//        String username = jwtService.getUsernameFromToken(token);
//
//        // Assert
//        assertEquals("test@example.com", username);
//    }
//
//    @Test
//    void getUsernameFromToken_InvalidToken_ReturnsNull() {
//        // Act
//        String username = jwtService.getUsernameFromToken("invalid.token");
//
//        // Assert
//        assertNull(username);
//    }
//}
