package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.PaymentMethod;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.common.exception.ConflictException;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.UnauthorizedException;
import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.*;
import com.mssus.app.repository.*;
import com.mssus.app.security.JwtService;
import com.mssus.app.service.AuthService;
import com.mssus.app.util.Constants;
import com.mssus.app.util.OtpUtil;
import com.mssus.app.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final WalletRepository walletRepository;
    private final VerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    
    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());
        
        // Validate unique constraints
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ConflictException.emailAlreadyExists(request.getEmail());
        }
        
        String normalizedPhone = ValidationUtil.normalizePhone(request.getPhone());
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw ConflictException.phoneAlreadyExists(normalizedPhone);
        }
        
        // Create user entity
        User user = User.builder()
                .email(request.getEmail())
                .phone(normalizedPhone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .userType(UserType.USER)
                .status(UserStatus.PENDING)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
        
        user = userRepository.save(user);
        
        // Create role-specific profile
        if ("rider".equalsIgnoreCase(request.getRole()) || request.getRole() == null) {
            createRiderProfile(user);
        } else if ("driver".equalsIgnoreCase(request.getRole())) {
            createRiderProfile(user); // Drivers also have rider profile
            // Driver profile will be created after verification
        }
        
        // Create wallet
        createWallet(user);
        
        // Generate JWT token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("active_role", user.getPrimaryRole());
        claims.put("iss", "mssus-app");
        String token = jwtService.generateToken(user.getEmail(), claims);
        
        return RegisterResponse.builder()
                .userId(user.getUserId())
                .userType("rider")
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .token(token)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for: {}", request.getEmailOrPhone());
        
        // Find user by email or phone
        String identifier = request.getEmailOrPhone();
        User user = ValidationUtil.isValidEmail(identifier)
                ? userRepository.findByEmail(identifier)
                    .orElseThrow(UnauthorizedException::invalidCredentials)
                : userRepository.findByPhone(ValidationUtil.normalizePhone(identifier))
                    .orElseThrow(UnauthorizedException::invalidCredentials);
        
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw UnauthorizedException.accountDisabled();
        }

        if (UserStatus.PENDING.equals(user.getStatus())) {
            throw UnauthorizedException.accountPending();
        }
        
        // Authenticate
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
        );
        
        // Generate tokens
        Map<String, Object> claims = buildTokenClaims(user);
//        Map<String, Object> claims = new HashMap<>();
//        claims.put("userId", user.getUserId());
//        claims.put("role", user.getPrimaryRole());
        
        String accessToken = jwtService.generateToken(user.getEmail(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        
        // Update last login for admin
        if (user.getAdminProfile() != null) {
            user.getAdminProfile().setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        }
        
        return LoginResponse.builder()
                .userId(user.getUserId())
                .userType(user.getUserType().name())
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationTime() / 1000) // Convert to seconds
                .build();
    }

    @Override
    public MessageResponse logout(String token) {
        // In stateless JWT, logout is handled client-side
        // Here we could blacklist the token if needed
        log.info("User logged out");
        return MessageResponse.of("Logged out successfully");
    }



    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getEmailOrPhone();
        User user = ValidationUtil.isValidEmail(identifier)
                ? userRepository.findByEmail(identifier).orElse(null)
                : userRepository.findByPhone(ValidationUtil.normalizePhone(identifier)).orElse(null);
        
        if (user == null) {
            // Don't reveal if user exists
            return MessageResponse.of("OTP sent to your registered contact");
        }
        
        // Generate and store OTP
        String otp = OtpUtil.generateOtp();
        String otpKey = user.getEmail() + ":" + Constants.OTP_FORGOT_PASSWORD;
        OtpUtil.storeOtp(otpKey, otp, Constants.OTP_FORGOT_PASSWORD);
        
        // TODO: Send OTP via email/SMS service
        log.info("OTP generated for password reset: {} (dev mode)", otp);
        
        return MessageResponse.of("OTP sent to your registered contact");
    }

    @Override
    public OtpResponse requestOtp(String username, String otpFor) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        // Generate OTP
        String otp = OtpUtil.generateOtp();
        String otpKey = user.getEmail() + ":" + otpFor;
        OtpUtil.storeOtp(otpKey, otp, otpFor);
        
        // TODO: Send OTP via email/SMS service
        log.info("OTP generated for {}: {} (dev mode)", otpFor, otp);
        
        return OtpResponse.builder()
                .message("OTP sent successfully")
                .otpFor(otpFor)
                .build();
    }

    @Override
    @Transactional
    public OtpResponse verifyOtp(OtpRequest request) {
        // Find user context from OTP
        // This is simplified - in production, maintain proper OTP-user mapping
        
        String otpFor = request.getOtpFor();
        
        if (Constants.OTP_FORGOT_PASSWORD.equals(otpFor)) {
            // Handle password reset
            // TODO: Implement proper user identification from OTP context
            return OtpResponse.builder()
                    .message("OTP verified successfully")
                    .otpFor(otpFor)
                    .build();
        }
        
        // For email/phone verification
        return OtpResponse.builder()
                .message("OTP verified successfully")
                .otpFor(otpFor)
                .verifiedField(Constants.OTP_VERIFY_EMAIL.equals(otpFor) ? "email" : "phone")
                .build();
    }

    private void createRiderProfile(User user) {
        RiderProfile riderProfile = RiderProfile.builder()
                .user(user)
                .totalRides(0)
                .totalSpent(BigDecimal.ZERO)
                .preferredPaymentMethod(PaymentMethod.WALLET)
                .build();

        riderProfileRepository.save(riderProfile);
    }

    private void createWallet(User user) {
        Wallet wallet = Wallet.builder()
                .user(user)
                .shadowBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .isActive(true)
                .build();

        walletRepository.save(wallet);
    }

    private Map<String, Object> buildTokenClaims(User user) {
        Map<String, Object> claims = new HashMap<>();

        // Standard claims
        claims.put("iss", "mssus.api");
        claims.put("sub", "user-" + user.getUserId());

        // User information
        claims.put("email", user.getEmail());

        // Roles array - all roles user has
        List<String> roles = getUserRoles(user);
        claims.put("roles", roles);

        // Active role - primary role or client-selected
        claims.put("active_role", user.getPrimaryRole());

        // Role status for ABAC (Attribute-Based Access Control)
        Map<String, String> roleStatus = buildRoleStatus(user);
        claims.put("role_status", roleStatus);

        // Token version for invalidation
        claims.put("token_version", user.getTokenVersion());

        return claims;
    }

    private List<String> getUserRoles(User user) {
        List<String> roles = new ArrayList<>();
        if (user.hasRole("ADMIN")) {
            roles.add("ADMIN");
            return roles; // Admin has all access, no need to check further
        }

        // Check all possible roles
        if (user.hasRole("RIDER")) {
            roles.add("RIDER");
        }

        if (user.hasRole("DRIVER") &&
            DriverProfileStatus.ACTIVE.equals(user.getDriverProfile().getStatus())) {
            roles.add("DRIVER");
        }

        return roles;
    }

    private Map<String, String> buildRoleStatus(User user) {
        Map<String, String> roleStatus = new HashMap<>();

        if (user.hasRole("ADMIN")) {
            roleStatus.put("ADMIN", "ACTIVE");
            return roleStatus; // Admin has all access, no need to check further
        }

        if (user.hasRole("RIDER")) {
            roleStatus.put("RIDER", user.getRiderProfile().getStatus().name());
        }

        if (user.hasRole("DRIVER")) {
            roleStatus.put("DRIVER", user.getDriverProfile().getStatus().name());
        }

        return roleStatus;
    }
}
