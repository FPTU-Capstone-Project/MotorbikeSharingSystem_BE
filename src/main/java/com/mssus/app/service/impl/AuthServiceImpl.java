package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.entity.*;
import com.mssus.app.repository.*;
import com.mssus.app.appconfig.security.JwtService;
import com.mssus.app.service.AuthService;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.RefreshTokenService;
import com.mssus.app.common.util.Constants;
import com.mssus.app.common.util.OtpUtil;
import com.mssus.app.common.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    public static Map<String, Object> userContext = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Validate email format
        if (!ValidationUtil.isValidEmail(request.getEmail())) {
            throw BaseDomainException.of("user.validation.invalid-email");
        }
        
        // Validate and sanitize full name to prevent XSS attacks
        if (!ValidationUtil.isValidFullName(request.getFullName())) {
            throw BaseDomainException.formatted("user.validation.invalid-fullname", 
                "Họ tên chứa ký tự không hợp lệ hoặc định dạng sai. Vui lòng chỉ sử dụng chữ cái, khoảng trắng và dấu gạch ngang.");
        }
        
        if (userRepository.existsByEmailAndStatusNot(request.getEmail(), UserStatus.EMAIL_VERIFYING)) {
            throw BaseDomainException.formatted("user.conflict.email-exists", "Email %s đã được đăng ký", request.getEmail());
        }
        if (userRepository.existsByEmailAndStatus(request.getEmail(), UserStatus.EMAIL_VERIFYING)) {
            throw BaseDomainException.formatted("user.conflict.email-exists", "Email %s đang chờ xác thực", request.getEmail());
        }
        String normalizedPhone = ValidationUtil.normalizePhone(request.getPhone());
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw BaseDomainException.formatted("user.conflict.phone-exists", "Số điện thoại %s đã được đăng ký", normalizedPhone);
        }
        
        // Sanitize full name as an extra layer of protection against XSS
        String sanitizedFullName = ValidationUtil.sanitizeText(request.getFullName().trim());
        
        User user = User.builder().email(request.getEmail()).phone(normalizedPhone).passwordHash(passwordEncoder.encode(request.getPassword())).fullName(sanitizedFullName).userType(UserType.USER).status(UserStatus.EMAIL_VERIFYING).emailVerified(false).phoneVerified(false).build();
        user = userRepository.save(user);
        Map<String, Object> claims = buildTokenClaims(user, null);
        String token = jwtService.generateToken(user.getEmail(), claims);
        return RegisterResponse.builder().userId(user.getUserId()).userType("rider").email(user.getEmail()).phone(user.getPhone()).fullName(user.getFullName()).token(token).createdAt(user.getCreatedAt()).build();
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

        // Allow login if email is verified (status = PENDING)
        // Rider profile is created separately through verification request, not required for login
        boolean isAdmin = UserType.ADMIN.equals(user.getUserType());
        boolean isEmailVerified = Boolean.TRUE.equals(user.getEmailVerified());

        if (!isAdmin) {
            // Check if email is verified - if not, require email verification
            if (!isEmailVerified) {
                // Email not verified - require email verification before login
                throw BaseDomainException.of("auth.unauthorized.email-verification-pending");
            }
            // Email is verified - allow login even if profile doesn't exist yet
            // Profile will be created separately through verification request to admin
            log.info("User {} attempting login with email verified. Profile may or may not exist.", user.getEmail());
        }

        validateUserBeforeGrantingToken(user);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
        );

        // Generate tokens
        Map<String, Object> claims = buildTokenClaims(user, 
                UserType.ADMIN.equals(user.getUserType()) ? null : request.getTargetProfile());

        var currentProfile = request.getTargetProfile();

        if (!UserType.ADMIN.equals(user.getUserType())) {
            if ("rider".equalsIgnoreCase(currentProfile)) {
                // Only update status if profile exists
                if (user.getRiderProfile() != null) {
                    user.getRiderProfile().setStatus(RiderProfileStatus.ACTIVE);
                    riderProfileRepository.save(user.getRiderProfile());
                }
                // Only flip driver runtime status if it was already ACTIVE/INACTIVE
                if (user.getDriverProfile() != null) {
                    DriverProfileStatus current = user.getDriverProfile().getStatus();
                    if (DriverProfileStatus.ACTIVE.equals(current) || DriverProfileStatus.INACTIVE.equals(current)) {
                        user.getDriverProfile().setStatus(DriverProfileStatus.INACTIVE);
                        driverProfileRepository.save(user.getDriverProfile());
                    }
                }
            } else if ("driver".equalsIgnoreCase(currentProfile)) {
                // Only update status if profile exists
                if (user.getDriverProfile() != null) {
                    DriverProfileStatus current = user.getDriverProfile().getStatus();
                    if (DriverProfileStatus.ACTIVE.equals(current) || DriverProfileStatus.INACTIVE.equals(current)) {
                        user.getDriverProfile().setStatus(DriverProfileStatus.ACTIVE);
                        driverProfileRepository.save(user.getDriverProfile());
                    }
                }
                // Profile might not exist yet - that's OK, user can still login
                if (user.getRiderProfile() != null) {
                    user.getRiderProfile().setStatus(RiderProfileStatus.INACTIVE);
                    riderProfileRepository.save(user.getRiderProfile());
                }
            }
        }

        userContext.put(user.getUserId().toString(), claims);
        String accessToken = jwtService.generateToken(user.getEmail(), claims);
        String refreshToken = refreshTokenService.generateRefreshToken(user);

        // Set activeProfile based on whether profile exists
        String activeProfile = null;
        if (!UserType.ADMIN.equals(user.getUserType())) {
            if ("rider".equalsIgnoreCase(currentProfile) && user.getRiderProfile() != null) {
                activeProfile = "rider";
            } else if ("driver".equalsIgnoreCase(currentProfile) && user.getDriverProfile() != null) {
                activeProfile = "driver";
            }
            // If profile doesn't exist yet, activeProfile remains null
        }

        return LoginResponse.builder()
                .userId(user.getUserId())
                .userType(user.getUserType().name())
                .activeProfile(activeProfile)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationTime() / 1000) // Convert to seconds
                .build();
    }
    @Override
    public MessageResponse logout(String refreshToken) {
        log.info("User logged out");
        refreshTokenService.invalidateRefreshToken(refreshToken);
        return MessageResponse.of("Đăng xuất thành công");
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
                .orElseThrow(() -> BaseDomainException.formatted("user.not-found.by-id", "Không tìm thấy người dùng với ID %s", userId));
        validateUserBeforeGrantingToken(user);

        // TODO: implement context persistence for refresh token
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
    @Override
    public void validateUserBeforeGrantingToken(User user) {
        if (UserStatus.SUSPENDED.equals(user.getStatus())) {
            throw BaseDomainException.of("auth.unauthorized.account-suspended");
        }

        if (UserStatus.DELETED.equals(user.getStatus())) {
            throw BaseDomainException.of("auth.unauthorized.account-deleted");
        }

        // Allow login if email is verified (status = PENDING or EMAIL_VERIFYING with email verified)
        // Flow: EMAIL_VERIFYING -> verify email -> PENDING (can login)
        if (UserStatus.EMAIL_VERIFYING.equals(user.getStatus())) {
            // Only block if email is NOT verified
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw BaseDomainException.of("auth.unauthorized.email-verification-pending");
        }
            // If email is verified, allow login (should be PENDING after verification, but allow just in case)
            log.info("User {} has EMAIL_VERIFYING status but email is verified. Allowing login.", user.getEmail());
        }

        // PENDING status is allowed - user has verified email and can login
        if (UserStatus.PENDING.equals(user.getStatus())) {
            log.debug("User {} with PENDING status attempting login - allowed (email verified).", user.getEmail());
        }
    }
    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getEmailOrPhone();
        User user = ValidationUtil.isValidEmail(identifier)
                ? userRepository.findByEmail(identifier).orElse(null)
                : userRepository.findByPhone(ValidationUtil.normalizePhone(identifier)).orElse(null);

        if (user == null) {
            return MessageResponse.of("Mã OTP đã được gửi đến thông tin liên lạc của bạn");
        }

        String otp = OtpUtil.generateOtp();
        String otpKey = user.getEmail() + ":" + Constants.OTP_FORGOT_PASSWORD;
        OtpUtil.storeOtp(otpKey, otp, OtpFor.FORGOT_PASSWORD);

        Map<String, Object> templateData = Map.of("fullName", user.getFullName(), "otp", otp);
        String templateName = "emails/otp-password-reset";
        String email = user.getEmail();
        String subject = "Password Reset OTP";
        
        emailService.sendEmail(email, subject, templateName, templateData, EmailPriority.HIGH, 
                Long.valueOf(user.getUserId()), "")
                .thenAccept(result -> log.info("Password reset OTP email sent to: {}", email))
                .exceptionally(ex -> {
                    log.error("Failed to send password reset OTP email to {}: {}", email, ex.getMessage());
                    return null;
                });

        return MessageResponse.of("Mã OTP đã được gửi đến thông tin liên lạc của bạn");
    }
    @Override
    public Map<String, Object> getUserContext(Integer userId) {
        Object context = userContext.get(userId.toString());
        if (context instanceof Map) {
            return (Map<String, Object>) context;
        }
        return null;
    }

    @Override
    public void setUserContext(Integer userId, Map<String, Object> context) {
        userContext.put(userId.toString(), context);
    }

    @Override
    public Map<String, Object> buildTokenClaims(User user, String activeProfile) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "mssus.api");
        claims.put("sub", "user-" + user.getUserId());
        claims.put("email", user.getEmail());
        claims.put("userId", user.getUserId());
        List<String> profiles = getUserProfiles(user);
        claims.put("profiles", profiles);
        claims.put("active_profile", activeProfile == null ? null : activeProfile.toUpperCase());
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
        Optional.ofNullable(user.getRiderProfile())
            .map(RiderProfile::getStatus)
            .ifPresent(status -> roleStatus.put("RIDER", status.name()));
        Optional.ofNullable(user.getDriverProfile())
            .map(DriverProfile::getStatus)
            .ifPresent(status -> roleStatus.put("DRIVER", status.name()));
        return roleStatus;
    }
}
