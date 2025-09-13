package com.mssus.app.service.impl;

import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.*;
import com.mssus.app.exception.*;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
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
        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .phone(normalizedPhone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .userType(Constants.USER_TYPE_STUDENT)
                .isActive(true)
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
        claims.put("role", user.getPrimaryRole());
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
        UserEntity user = ValidationUtil.isValidEmail(identifier) 
                ? userRepository.findByEmail(identifier)
                    .orElseThrow(UnauthorizedException::invalidCredentials)
                : userRepository.findByPhone(ValidationUtil.normalizePhone(identifier))
                    .orElseThrow(UnauthorizedException::invalidCredentials);
        
        if (!user.getIsActive()) {
            throw UnauthorizedException.accountDisabled();
        }
        
        // Authenticate
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
        );
        
        // Generate tokens
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", user.getPrimaryRole());
        
        String accessToken = jwtService.generateToken(user.getEmail(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        
        // Update last login for admin
        if (user.getAdminProfile() != null) {
            user.getAdminProfile().setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        }
        
        return LoginResponse.builder()
                .userId(user.getUserId())
                .userType(user.getPrimaryRole().toLowerCase())
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
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        UserEntity user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        return mapToUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        // Update basic info
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        
        // Handle email change
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw ConflictException.emailAlreadyExists(request.getEmail());
            }
            user.setEmail(request.getEmail());
            user.setEmailVerified(false); // Require re-verification
        }
        
        // Handle phone change
        if (request.getPhone() != null) {
            String normalizedPhone = ValidationUtil.normalizePhone(request.getPhone());
            if (!normalizedPhone.equals(user.getPhone())) {
                if (userRepository.existsByPhone(normalizedPhone)) {
                    throw ConflictException.phoneAlreadyExists(normalizedPhone);
                }
                user.setPhone(normalizedPhone);
                user.setPhoneVerified(false); // Require re-verification
            }
        }
        
        // Update rider profile specific fields
        if (user.getRiderProfile() != null) {
            if (request.getPreferredPaymentMethod() != null) {
                user.getRiderProfile().setPreferredPaymentMethod(request.getPreferredPaymentMethod());
            }
            if (request.getEmergencyContact() != null) {
                user.getRiderProfile().setEmergencyContact(request.getEmergencyContact());
            }
        }
        
        user = userRepository.save(user);
        return mapToUserProfileResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse updatePassword(String username, UpdatePasswordRequest request) {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw ValidationException.passwordMismatch();
        }
        
        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("Password updated for user: {}", username);
        return MessageResponse.of("Password updated successfully");
    }

    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getEmailOrPhone();
        UserEntity user = ValidationUtil.isValidEmail(identifier)
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
        UserEntity user = userRepository.findByEmail(username)
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

    @Override
    @Transactional
    public MessageResponse updateAvatar(String username, MultipartFile file) {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        // Validate file
        if (file.isEmpty()) {
            throw new ValidationException("File is empty");
        }
        
        if (file.getSize() > Constants.MAX_IMAGE_SIZE) {
            throw ValidationException.fileTooLarge(Constants.MAX_IMAGE_SIZE);
        }
        
        // TODO: Implement file storage service
        String fileUrl = "https://cdn.example.com/avatars/" + user.getUserId() + ".jpg";
        
        user.setProfilePhotoUrl(fileUrl);
        userRepository.save(user);
        
        return MessageResponse.of("Avatar updated successfully");
    }

    @Override
    @Transactional
    public MessageResponse switchProfile(String username, SwitchProfileRequest request) {
        UserEntity user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        String targetRole = request.getTargetRole().toLowerCase();
        
        // Validate user has the target profile
        if ("driver".equals(targetRole)) {
            if (user.getDriverProfile() == null) {
                throw new ValidationException("You don't have a driver profile");
            }
            if (!Constants.STATUS_ACTIVE.equals(user.getDriverProfile().getStatus())) {
                throw UnauthorizedException.profileNotActive("Driver");
            }
        } else if ("rider".equals(targetRole)) {
            if (user.getRiderProfile() == null) {
                throw new ValidationException("You don't have a rider profile");
            }
        } else {
            throw new ValidationException("Invalid target role: " + targetRole);
        }
        
        // Note: Actual role switching is handled by generating new tokens
        return MessageResponse.of("Switched to " + targetRole + " profile");
    }

    @Override
    @Transactional
    public VerificationResponse submitStudentVerification(String username, MultipartFile document) {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        // Check if already verified
        if (verificationRepository.isUserVerifiedForType(user.getUserId(), Constants.VERIFICATION_STUDENT_ID)) {
            throw new ConflictException("Student verification already approved");
        }
        
        // TODO: Implement file storage
        String documentUrl = "https://cdn.example.com/verifications/student_" + user.getUserId() + ".jpg";
        
        VerificationEntity verification = VerificationEntity.builder()
                .user(user)
                .type(Constants.VERIFICATION_STUDENT_ID)
                .status(Constants.STATUS_PENDING)
                .documentUrl(documentUrl)
                .documentType(Constants.FILE_TYPE_IMAGE)
                .build();
        
        verification = verificationRepository.save(verification);
        
        return VerificationResponse.builder()
                .verificationId(verification.getVerificationId())
                .status(verification.getStatus())
                .type(verification.getType())
                .documentUrl(verification.getDocumentUrl())
                .build();
    }

    @Override
    @Transactional
    public VerificationResponse submitDriverVerification(String username, DriverVerificationRequest request) {
        UserEntity user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        
        // Check if driver profile already exists
        if (user.getDriverProfile() != null) {
            throw ConflictException.profileAlreadyExists("Driver");
        }
        
        // Check license number uniqueness
        if (driverProfileRepository.existsByLicenseNumber(request.getLicenseNumber())) {
            throw ConflictException.licenseNumberAlreadyExists(request.getLicenseNumber());
        }
        
        // Create driver profile with pending status
        DriverProfileEntity driverProfile = DriverProfileEntity.builder()
                .user(user)
                .licenseNumber(request.getLicenseNumber())
                .status(Constants.STATUS_PENDING)
                .ratingAvg(Constants.DEFAULT_RATING)
                .totalSharedRides(0)
                .totalEarned(BigDecimal.ZERO)
                .commissionRate(new BigDecimal(Constants.DEFAULT_COMMISSION_RATE))
                .isAvailable(false)
                .maxPassengers(Constants.DEFAULT_MAX_PASSENGERS)
                .build();
        
        driverProfileRepository.save(driverProfile);
        
        // TODO: Create vehicle record and verifications
        
        return VerificationResponse.builder()
                .verificationId(1) // Placeholder
                .status(Constants.STATUS_PENDING)
                .type(Constants.VERIFICATION_DRIVER_LICENSE)
                .build();
    }

    private void createRiderProfile(UserEntity user) {
        RiderProfileEntity riderProfile = RiderProfileEntity.builder()
                .user(user)
                .ratingAvg(Constants.DEFAULT_RATING)
                .totalRides(0)
                .totalSpent(BigDecimal.ZERO)
                .preferredPaymentMethod(Constants.PAYMENT_METHOD_WALLET)
                .build();
        
        riderProfileRepository.save(riderProfile);
    }

    private void createWallet(UserEntity user) {
        WalletEntity wallet = WalletEntity.builder()
                .user(user)
                .cachedBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalToppedUp(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .isActive(true)
                .build();
        
        walletRepository.save(wallet);
    }

    private UserProfileResponse mapToUserProfileResponse(UserEntity user) {
        UserProfileResponse.UserInfo userInfo = UserProfileResponse.UserInfo.builder()
                .userId(user.getUserId())
                .userType(user.getPrimaryRole().toLowerCase())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .studentId(user.getStudentId())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .isActive(user.getIsActive())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .build();
        
        UserProfileResponse.UserProfileResponseBuilder responseBuilder = UserProfileResponse.builder()
                .user(userInfo);
        
        // Map rider profile
        if (user.getRiderProfile() != null) {
            RiderProfileEntity rider = user.getRiderProfile();
            responseBuilder.riderProfile(UserProfileResponse.RiderProfile.builder()
                    .emergencyContact(rider.getEmergencyContact())
                    .ratingAvg(rider.getRatingAvg())
                    .totalRides(rider.getTotalRides())
                    .totalSpent(rider.getTotalSpent())
                    .preferredPaymentMethod(rider.getPreferredPaymentMethod())
                    .build());
        }
        
        // Map driver profile
        if (user.getDriverProfile() != null) {
            DriverProfileEntity driver = user.getDriverProfile();
            responseBuilder.driverProfile(UserProfileResponse.DriverProfile.builder()
                    .licenseNumber(driver.getLicenseNumber())
                    .status(driver.getStatus())
                    .ratingAvg(driver.getRatingAvg())
                    .totalSharedRides(driver.getTotalSharedRides())
                    .totalEarned(driver.getTotalEarned())
                    .commissionRate(driver.getCommissionRate())
                    .isAvailable(driver.getIsAvailable())
                    .maxPassengers(driver.getMaxPassengers())
                    .build());
        }
        
        // Map wallet
        if (user.getWallet() != null) {
            WalletEntity wallet = user.getWallet();
            responseBuilder.wallet(UserProfileResponse.WalletInfo.builder()
                    .walletId(wallet.getWalletId())
                    .cachedBalance(wallet.getCachedBalance())
                    .pendingBalance(wallet.getPendingBalance())
                    .isActive(wallet.getIsActive())
                    .build());
        }
        
        return responseBuilder.build();
    }
}
