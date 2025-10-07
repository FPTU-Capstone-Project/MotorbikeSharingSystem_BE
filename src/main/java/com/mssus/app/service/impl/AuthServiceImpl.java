package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.*;
import com.mssus.app.repository.*;
import com.mssus.app.security.JwtService;
import com.mssus.app.service.AuthService;
import com.mssus.app.service.RefreshTokenService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    public static Map<String, Object> userContext = new ConcurrentHashMap<>(); //TODO: improve context persistence later

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        if (userRepository.existsByEmailAndStatusNot(request.getEmail(), UserStatus.EMAIL_VERIFYING)) {
            throw BaseDomainException.formatted("user.conflict.email-exists", "Email %s already registered", request.getEmail());
        }

        if (userRepository.existsByEmailAndStatus(request.getEmail(), UserStatus.EMAIL_VERIFYING)) {
            throw BaseDomainException.formatted("user.conflict.email-exists", "Email %s is pending verification", request.getEmail());
        }

        String normalizedPhone = ValidationUtil.normalizePhone(request.getPhone());
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw BaseDomainException.formatted("user.conflict.phone-exists", "Phone %s already registered", normalizedPhone);
        }


        User user = User.builder()
            .email(request.getEmail())
            .phone(normalizedPhone)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName())
            .userType(UserType.USER)
            .status(UserStatus.EMAIL_VERIFYING)
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
        Map<String, Object> claims = buildTokenClaims(user, null);
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
        log.info("Login attempt for: {}", request.getEmail());

        String identifier = request.getEmail();
        User user = userRepository.findByEmail(identifier)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email"));

        if (!ValidationUtil.isValidEmail(request.getEmail())) {
            throw BaseDomainException.of("user.validation.invalid-email");
        }

        if (!user.hasProfile(request.getTargetProfile()) && !UserType.ADMIN.equals(user.getUserType())) {
            throw BaseDomainException.formatted("user.validation.profile-not-exists", "User does not have profile: %s", request.getTargetProfile());
        }

        validateUserBeforeGrantingToken(user);


        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
        );

        // Generate tokens
        Map<String, Object> claims = buildTokenClaims(user, UserType.ADMIN.equals(user.getUserType()) ? null : request.getTargetProfile());

        //Persist context for refresh token use
        userContext.put(user.getUserId().toString(), claims);

        String accessToken = jwtService.generateToken(user.getEmail(), claims);
        String refreshToken = refreshTokenService.generateRefreshToken(user);

        return LoginResponse.builder()
            .userId(user.getUserId())
            .userType(user.getUserType().name())
            .activeProfile(UserType.ADMIN.equals(user.getUserType()) ? null : request.getTargetProfile())
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(jwtService.getExpirationTime() / 1000) // Convert to seconds
            .build();
    }

    @Override
    public MessageResponse logout(String refreshToken) {
        log.info("User logged out");
        refreshTokenService.invalidateRefreshToken(refreshToken);
        return MessageResponse.of("Logged out successfully");
    }

    @Override
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        log.info("Refreshing access token");

        String requestRefreshToken = request.refreshToken();
        if (!refreshTokenService.validateRefreshToken(requestRefreshToken)) {
            throw BaseDomainException.of("auth.unauthorized.invalid-refresh-token");
        }

        String userId = refreshTokenService.getUserIdFromRefreshToken(requestRefreshToken);
        if (userId == null) {
            throw BaseDomainException.of("auth.unauthorized.invalid-refresh-token");
        }

        User user = userRepository.findById(Integer.valueOf(userId))
            .orElseThrow(() -> BaseDomainException.formatted("user.not-found.by-id", "User with ID %s not found", userId));

        validateUserBeforeGrantingToken(user);

        //TODO: implement context persistence for refresh token
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = Optional.ofNullable(userContext.get(userId))
            .filter(Map.class::isInstance)
            .map(obj -> (Map<String, Object>) obj)
            .orElseGet(() -> buildTokenClaims(user, null));

        String newAccessToken = jwtService.generateToken(user.getEmail(), claims);

        return TokenRefreshResponse.builder()
            .accessToken(newAccessToken)
            .build();
    }

    public void validateUserBeforeGrantingToken(User user) {
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw BaseDomainException.of("auth.unauthorized.account-suspended");
        }

        if (UserStatus.PENDING.equals(user.getStatus())) {
            throw BaseDomainException.of("auth.unauthorized.account-pending");
        }

        if (UserStatus.EMAIL_VERIFYING.equals(user.getStatus())) {
            throw BaseDomainException.of("auth.unauthorized.email-verification-pending");
        }
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
        OtpUtil.storeOtp(otpKey, otp, OtpFor.FORGOT_PASSWORD);

        // TODO: Send OTP via email/SMS service
        log.info("OTP generated for password reset: {} (dev mode)", otp);

        return MessageResponse.of("OTP sent to your registered contact");
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

    public Map<String, Object> buildTokenClaims(User user, String activeProfile) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "mssus.api");
        claims.put("sub", "user-" + user.getUserId());
        claims.put("email", user.getEmail());

        List<String> profiles = getUserProfiles(user);
        claims.put("profiles", profiles);
        claims.put("active_profile", activeProfile);

        Map<String, String> profileStatus = buildProfileStatus(user);
        claims.put("profile_status", profileStatus);

        claims.put("token_version", user.getTokenVersion());

        return claims;
    }

    public List<String> getUserProfiles(User user) {
        List<String> profiles = new ArrayList<>();

        if (user.hasProfile("RIDER")) {
            profiles.add("RIDER");
        }

        if (user.hasProfile("DRIVER")) {
            profiles.add("DRIVER");
        }

        return profiles;
    }

    public Map<String, String> buildProfileStatus(User user) {
        Map<String, String> roleStatus = new HashMap<>();

        if (user.hasProfile("RIDER")) {
            roleStatus.put("RIDER", user.getRiderProfile().getStatus().name());
        }

        if (user.hasProfile("DRIVER")) {
            roleStatus.put("DRIVER", user.getDriverProfile().getStatus().name());
        }

        return roleStatus;
    }
}
