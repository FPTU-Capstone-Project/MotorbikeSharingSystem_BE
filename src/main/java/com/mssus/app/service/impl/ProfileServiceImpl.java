package com.mssus.app.service.impl;

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
import com.mssus.app.mapper.VerificationMapper;
import com.mssus.app.repository.*;
import com.mssus.app.security.JwtService;
import com.mssus.app.service.AuthService;
import com.mssus.app.service.FileUploadService;
import com.mssus.app.service.ProfileService;
import com.mssus.app.util.Constants;
import com.mssus.app.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.Authentication;
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
    private final UserMapper userMapper;
    private final VerificationMapper verificationMapper;
    private final AuthServiceImpl authService;
    private final JwtService jwtService;
    private final FileUploadService fileUploadService;

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

    @Override
    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 3)
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
//        User user = findUserWithLock(username);
//        updateBasicInfo(user, request);
//        updateEmailIfChanged(user, request);
//        updatePhoneIfChanged(user, request);
//        updateRiderProfileIfExists(user, request);
//
//        user = userRepository.save(user);
////        return userMapper.toProfileResponse(user);
//        return UserProfileResponse.builder().build();
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
        try {
            // Check if already verified
            if (verificationRepository.isUserVerifiedForType(user.getUserId(), VerificationType.STUDENT_ID)) {
                throw ConflictException.of("Student verification already approved");
            }

            String documentUrl = fileUploadService.uploadFile(document).get();

            Verification verification = Verification.builder()
                    .user(user)
                    .type(VerificationType.STUDENT_ID)
                    .status(VerificationStatus.PENDING)
                    .documentUrl(documentUrl)
                    .documentType(DocumentType.IMAGE)
                    .build();

            verification = verificationRepository.save(verification);

            return verificationMapper.mapToVerificationResponse(verification);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload document: " + e.getMessage());
        }
    }

    @Override
    public MessageResponse updateAvatar(String username, MultipartFile avatarFile) {
        User user = userRepository.findByEmail(username).orElseThrow(() -> new RuntimeException("User not found"));
        try {
            String profilePhotoUrl = fileUploadService.uploadFile(avatarFile).get();
            user.setProfilePhotoUrl(profilePhotoUrl);
            userRepository.save(user);
            return MessageResponse.builder()
                    .message("Avatar uploaded successfully")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload avatar: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public VerificationResponse submitDriverVerification(String username, DriverVerificationRequest request) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        try {
            if (user.getDriverProfile() != null) {
                throw ConflictException.profileAlreadyExists("Driver");
            }
            if (user.getRiderProfile() == null){
                throw ValidationException.of("Rider profile must be created before applying for Driver profile");
            }

            if (driverProfileRepository.existsByLicenseNumber(request.getLicenseNumber())) {
                throw ConflictException.licenseNumberAlreadyExists(request.getLicenseNumber());
            }

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

            String documentUrl = fileUploadService.uploadFile(request.getDocumentProof()).get();

            Verification verification = Verification.builder()
                    .user(user)
                    .type(VerificationType.DRIVER_LICENSE)
                    .status(VerificationStatus.PENDING)
                    .documentUrl(documentUrl)
                    .documentType(DocumentType.IMAGE)
                    .build();
            verificationRepository.save(verification);
            return verificationMapper.mapToVerificationResponse(verification);
        } catch (Exception e) {
            throw new RuntimeException("Failed to submit driver verification: " + e.getMessage());
        }
    }

    // --- Helper Methods ---

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
