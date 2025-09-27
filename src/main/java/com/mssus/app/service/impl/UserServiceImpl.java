package com.mssus.app.service.impl;


import com.mssus.app.dto.request.DriverVerificationRequest;
import com.mssus.app.dto.request.SwitchProfileRequest;
import com.mssus.app.dto.request.UpdatePasswordRequest;
import com.mssus.app.dto.request.UpdateProfileRequest;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.entity.*;
import com.mssus.app.exception.ConflictException;
import com.mssus.app.exception.NotFoundException;
import com.mssus.app.exception.UnauthorizedException;
import com.mssus.app.exception.ValidationException;
import com.mssus.app.repository.*;
import com.mssus.app.service.UserService;
import com.mssus.app.util.Constants;
import com.mssus.app.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        Users user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        return mapToUserProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserProfileResponse> getAllUserProfiles() {
        List<Users> users = userRepository.findAll();
        return users.stream().map(this::mapToUserProfileResponse).toList();
    }

    @Override
    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 3)
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        Users user = findUserWithLock(username);
        updateBasicInfo(user, request);
        updateEmailIfChanged(user, request);
        updatePhoneIfChanged(user, request);
        updateRiderProfileIfExists(user, request);

        user = userRepository.save(user);
        return mapToUserProfileResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse updatePassword(String username, UpdatePasswordRequest request) {
        Users user = userRepository.findByEmail(username)
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
        Users user = userRepository.findByEmail(username)
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
        Users user = userRepository.findByEmailWithProfiles(username)
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
        Users user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        // Check if already verified
        if (verificationRepository.isUserVerifiedForType(user.getUserId(), Constants.VERIFICATION_STUDENT_ID)) {
            throw new ConflictException("Student verification already approved");
        }

        // TODO: Implement file storage
        String documentUrl = "https://cdn.example.com/verifications/student_" + user.getUserId() + ".jpg";

        Verification verification = Verification.builder()
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
        Users user = userRepository.findByEmail(username)
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


    // --- Helper Methods ---
    private UserProfileResponse mapToUserProfileResponse(Users user) {
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
            RiderProfile rider = user.getRiderProfile();
            responseBuilder.riderProfile(UserProfileResponse.RiderProfile.builder()
                    .emergencyContact(rider.getEmergencyContact())
                    .totalRides(rider.getTotalRides())
                    .totalSpent(rider.getTotalSpent())
                    .preferredPaymentMethod(rider.getPreferredPaymentMethod())
                    .build());
        }

        // Map driver profile
        if (user.getDriverProfile() != null) {
            DriverProfile driver = user.getDriverProfile();
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
            Wallet wallet = user.getWallet();
            responseBuilder.wallet(UserProfileResponse.WalletInfo.builder()
                    .walletId(wallet.getWalletId())
//                    .shadowBalance(wallet.getShadowBalance())
                    .pendingBalance(wallet.getPendingBalance())
                    .isActive(wallet.getIsActive())
                    .build());
        }

        return responseBuilder.build();
    }

    private Users findUserWithLock(String username) {
        return userRepository.findByEmailWithLock(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
    }

    private void updateBasicInfo(Users user, UpdateProfileRequest request) {
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
    }

    private void updateEmailIfChanged(Users user, UpdateProfileRequest request) {
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            validateEmailUniqueness(request.getEmail());
            String oldEmail = user.getEmail();
            user.setEmail(request.getEmail());
            user.setEmailVerified(false);
        }
    }

    private void updatePhoneIfChanged(Users user, UpdateProfileRequest request) {
        if (request.getPhone() != null) {
            String normalizedPhone = ValidationUtil.normalizePhone(request.getPhone());
            if (!normalizedPhone.equals(user.getPhone())) {
                validatePhoneUniqueness(normalizedPhone);
                String oldPhone = user.getPhone();
                user.setPhone(normalizedPhone);
                user.setPhoneVerified(false);
            }
        }
    }

    private void updateRiderProfileIfExists(Users user, UpdateProfileRequest request) {
        if (user.getRiderProfile() != null) {
            updateRiderProfileFields(user.getRiderProfile(), request);
        }
    }

    private void updateRiderProfileFields(RiderProfile riderProfile, UpdateProfileRequest request) {
        if (request.getPreferredPaymentMethod() != null) {
            riderProfile.setPreferredPaymentMethod(request.getPreferredPaymentMethod());
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
