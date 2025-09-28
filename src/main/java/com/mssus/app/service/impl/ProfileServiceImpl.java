package com.mssus.app.service.impl;

import com.mssus.app.BaseEvent.EmailChangedEvent;
import com.mssus.app.BaseEvent.PhoneChangedEvent;
import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.*;
import com.mssus.app.dto.request.DriverVerificationRequest;
import com.mssus.app.dto.request.SwitchProfileRequest;
import com.mssus.app.dto.request.UpdatePasswordRequest;
import com.mssus.app.dto.request.UpdateProfileRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.SwitchProfileResponse;
import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.UserMapper;
import com.mssus.app.repository.*;
import com.mssus.app.security.JwtService;
import com.mssus.app.service.AuthService;
import com.mssus.app.service.ProfileService;
import com.mssus.app.util.Constants;
import com.mssus.app.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;
    private final AuthServiceImpl authService;
    private final JwtService jwtService;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        User user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        if (UserType.ADMIN.equals(user.getUserType())) {
            return userMapper.toAdminProfileResponse(user);
        }

        String activeProfile = Optional.ofNullable(AuthServiceImpl.userContext.get(user.getUserId().toString()))
            .filter(Map.class::isInstance)
            .map(obj -> (Map<String, Object>) obj)
            .map(claims -> (String) claims.get("active_profile"))
            .orElse(null);

        List<String> availableProfiles = Optional.ofNullable(AuthServiceImpl.userContext.get(user.getUserId().toString()))
            .filter(Map.class::isInstance)
            .map(obj -> (Map<String, Object>) obj)
            .map(claims -> (List<String>) claims.get("profiles"))
            .map(profiles -> profiles.stream()
                .filter(profile -> user.isProfileActive(profile))
                .toList())
            .orElse(List.of());

        return switch (UserProfileType.valueOf(activeProfile.toUpperCase())) {
            case DRIVER -> {
                UserProfileResponse response = userMapper.toDriverProfileResponse(user);
                response.setAvailableProfiles(availableProfiles);
                response.setActiveProfile(activeProfile);
                yield response;
            }
            case RIDER -> {
                UserProfileResponse response = userMapper.toRiderProfileResponse(user);
                response.setAvailableProfiles(availableProfiles);
                response.setActiveProfile(activeProfile);
                yield response;
            }
        };
    }
//
//    @Override
//    @Transactional(readOnly = true)
//    public List<UserProfileResponse> getAllUserProfiles() {
//        List<User> users = userRepository.findAll();
//        return users.stream().map(this::mapToUserProfileResponse).toList();
//    }

    @Override
    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 3)
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = findUserWithLock(username);
        updateBasicInfo(user, request);
        updateEmailIfChanged(user, request);
        updatePhoneIfChanged(user, request);
        updateRiderProfileIfExists(user, request);

        user = userRepository.save(user);
//        return userMapper.toProfileResponse(user);
        return UserProfileResponse.builder().build();
    }

    @Override
    @Transactional
    public MessageResponse updatePassword(String username, UpdatePasswordRequest request) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw ValidationException.passwordMismatch();
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return MessageResponse.of("Password updated successfully");
    }

    @Override
    @Transactional
    public MessageResponse updateAvatar(String username, MultipartFile file) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        // Validate file
        if (file.isEmpty()) {
            throw ValidationException.of("File is empty");
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
    public SwitchProfileResponse switchProfile(String username, SwitchProfileRequest request) {
        User user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email", "User with email not found: " + username));

        String targetProfile = request.getTargetProfile();

        switch (UserProfileType.valueOf(targetProfile.toUpperCase())) {
            case DRIVER -> {
                if (user.getDriverProfile() == null) {
                    throw BaseDomainException.of("user.validation.profile-not-exists");
                }
                if (!DriverProfileStatus.ACTIVE.equals(user.getDriverProfile().getStatus())) {
                    throw BaseDomainException.of("user.validation.profile-not-active", "Driver profile is not active");
                }
            }
            case RIDER -> {
                if (user.getRiderProfile() == null) {
                    throw BaseDomainException.of("user.validation.profile-not-exists");
                }
            }
            default -> throw ValidationException.of("Invalid target role: " + targetProfile);
        }

        authService.validateUserBeforeGrantingToken(user);

        Map<String, Object> claims = authService.buildTokenClaims(user, request.getTargetProfile());

        AuthServiceImpl.userContext.put(user.getUserId().toString(), claims);

        String accessToken = jwtService.generateToken(user.getEmail(), claims);

        return SwitchProfileResponse.builder()
                .accessToken(accessToken)
                .activeProfile(request.getTargetProfile())
                .build();
    }

    @Override
    @Transactional
    public VerificationResponse submitStudentVerification(String username, MultipartFile document) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        // Check if already verified
        if (verificationRepository.isUserVerifiedForType(user.getUserId(), VerificationType.STUDENT_ID)) {
            throw ConflictException.of("Student verification already approved");
        }

        // TODO: Implement file storage
        String documentUrl = "https://cdn.example.com/verifications/student_" + user.getUserId() + ".jpg";

        Verification verification = Verification.builder()
                .user(user)
                .type(VerificationType.STUDENT_ID)
                .status(VerificationStatus.PENDING)
                .documentUrl(documentUrl)
                .documentType(DocumentType.IMAGE)
                .build();

        verification = verificationRepository.save(verification);

        return VerificationResponse.builder()
                .verificationId(verification.getVerificationId())
                .status(verification.getStatus().name())
                .type(verification.getType().name())
                .documentUrl(verification.getDocumentUrl())
                .build();
    }

    @Override
    @Transactional
    public VerificationResponse submitDriverVerification(String username, DriverVerificationRequest request) {
        User user = userRepository.findByEmail(username)
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
        DriverProfile driverProfile = DriverProfile.builder()
                .user(user)
                .licenseNumber(request.getLicenseNumber())
                .status(DriverProfileStatus.PENDING)
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
                .status(VerificationStatus.PENDING.name())
                .type(VerificationType.DRIVER_LICENSE.name())
                .build();
    }


//    // --- Helper Methods ---
//    private UserProfileResponse mapToUserProfileResponse(User user) {
//        UserProfileResponse.UserInfo userInfo = UserProfileResponse.UserInfo.builder()
//                .userId(user.getUserId())
//                .userType(user.getPrimaryRole().toLowerCase())
//                .email(user.getEmail())
//                .phone(user.getPhone())
//                .fullName(user.getFullName())
//                .studentId(user.getStudentId())
//                .profilePhotoUrl(user.getProfilePhotoUrl())
//                .userStatus(user.getUserStatus().name())
//                .emailVerified(user.getEmailVerified())
//                .phoneVerified(user.getPhoneVerified())
//                .build();
//
//        UserProfileResponse.UserProfileResponseBuilder responseBuilder = UserProfileResponse.builder()
//                .user(userInfo);
//
//        // Map rider profile
//        if (user.getRiderProfile() != null) {
//            RiderProfile rider = user.getRiderProfile();
//            responseBuilder.riderProfile(UserProfileResponse.RiderProfile.builder()
//                    .emergencyContact(rider.getEmergencyContact())
//                    .totalRides(rider.getTotalRides())
//                    .totalSpent(rider.getTotalSpent())
//                    .preferredPaymentMethod(rider.getPreferredPaymentMethod())
//                    .build());
//        }
//
//        // Map driver profile
//        if (user.getDriverProfile() != null) {
//            DriverProfile driver = user.getDriverProfile();
//            responseBuilder.driverProfile(UserProfileResponse.DriverProfile.builder()
//                    .licenseNumber(driver.getLicenseNumber())
//                    .status(driver.getStatus())
//                    .ratingAvg(driver.getRatingAvg())
//                    .totalSharedRides(driver.getTotalSharedRides())
//                    .totalEarned(driver.getTotalEarned())
//                    .commissionRate(driver.getCommissionRate())
//                    .isAvailable(driver.getIsAvailable())
//                    .maxPassengers(driver.getMaxPassengers())
//                    .build());
//        }
//
//        // Map wallet
//        if (user.getWallet() != null) {
//            Wallet wallet = user.getWallet();
//            responseBuilder.wallet(UserProfileResponse.WalletInfo.builder()
//                    .walletId(wallet.getWalletId())
////                    .shadowBalance(wallet.getShadowBalance())
//                    .pendingBalance(wallet.getPendingBalance())
//                    .isActive(wallet.getIsActive())
//                    .build());
//        }
//
//        return responseBuilder.build();
//    }

    private User findUserWithLock(String username) {
        return userRepository.findByEmailWithLock(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
    }

    private void updateBasicInfo(User user, UpdateProfileRequest request) {
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
    }

    private void updateEmailIfChanged(User user, UpdateProfileRequest request) {
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            validateEmailUniqueness(request.getEmail());
            String oldEmail = user.getEmail();
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);
            eventPublisher.publishEvent(new EmailChangedEvent(user.getUserId(), oldEmail, request.getEmail()));
        }
    }

    private void updatePhoneIfChanged(User user, UpdateProfileRequest request) {
        if (request.getPhone() != null) {
            String normalizedPhone = ValidationUtil.normalizePhone(request.getPhone());
            if (!normalizedPhone.equals(user.getPhone())) {
                validatePhoneUniqueness(normalizedPhone);
                String oldPhone = user.getPhone();
                user.setPhone(normalizedPhone);
                user.setPhoneVerified(false);
                eventPublisher.publishEvent(new PhoneChangedEvent(user.getUserId(), oldPhone, request.getPhone()));
            }
        }
    }

    private void updateRiderProfileIfExists(User user, UpdateProfileRequest request) {
        if (user.getRiderProfile() != null) {
            updateRiderProfileFields(user.getRiderProfile(), request);
        }
    }

    private void updateRiderProfileFields(RiderProfile riderProfile, UpdateProfileRequest request) {
        if (request.getPreferredPaymentMethod() != null) {
            riderProfile.setPreferredPaymentMethod(PaymentMethod.valueOf(request.getPreferredPaymentMethod()));
        }
        if (request.getEmergencyContact() != null) {
            riderProfile.setEmergencyContact(request.getEmergencyContact());
        }
    }

    private void validateEmailUniqueness(String email) {
        if (userRepository.existsByEmail(email)) {
            throw ConflictException.emailAlreadyExists(email);
        }
    }

    private void validatePhoneUniqueness(String phone) {
        if (userRepository.existsByPhone(phone)) {
            throw ConflictException.phoneAlreadyExists(phone);
        }
    }
    // --- Helper Methods ---
}
