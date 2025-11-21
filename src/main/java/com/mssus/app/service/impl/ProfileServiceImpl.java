package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.*;
import com.mssus.app.dto.request.SwitchProfileRequest;
import com.mssus.app.dto.request.UpdatePasswordRequest;
import com.mssus.app.dto.request.UpdateProfileRequest;
import com.mssus.app.dto.response.*;
import com.mssus.app.dto.domain.sos.EmergencyContactRequest;
import com.mssus.app.dto.domain.sos.EmergencyContactResponse;
import com.mssus.app.service.AuthService;
import com.mssus.app.service.EmergencyContactService;
import com.mssus.app.service.FPTAIService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.UserMapper;
import com.mssus.app.mapper.VerificationMapper;
import com.mssus.app.repository.*;
import com.mssus.app.appconfig.security.JwtService;
import com.mssus.app.service.FileUploadService;
import com.mssus.app.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {
    private final UserRepository userRepository;
    private final VerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final DriverProfileRepository driverProfileRepository;
    private final UserMapper userMapper;
    private final VerificationMapper verificationMapper;
    private final AuthService authService;
    private final JwtService jwtService;
    private final FileUploadService fileUploadService;
    private final FPTAIService fptaiService;
    private final EmergencyContactService emergencyContactService;


    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String username) {
        User user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        List<EmergencyContactResponse> emergencyContacts = emergencyContactService.getContacts(user);

        if (UserType.ADMIN.equals(user.getUserType())) {
            UserProfileResponse response = userMapper.toAdminProfileResponse(user);
            response.setEmergencyContacts(emergencyContacts);
            return response;
        }

        String activeProfile = Optional.ofNullable(authService.getUserContext(user.getUserId()))
                .filter(Map.class::isInstance)
                .map(claims -> (String) claims.get("active_profile"))
                .orElse(null);

        List<String> availableProfiles = Optional.ofNullable(authService.getUserContext(user.getUserId()))
                .filter(Map.class::isInstance)
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
                response.setEmergencyContacts(emergencyContacts);
                yield response;
            }
            case RIDER -> {
                UserProfileResponse response = userMapper.toRiderProfileResponse(user);
                response.setAvailableProfiles(availableProfiles);
                response.setActiveProfile(activeProfile);
                response.setEmergencyContacts(emergencyContacts);
                yield response;
            }
        };
    }

    @Override
    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 3)
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        // This method is reserved for admin updates
        throw new UnsupportedOperationException("Sử dụng updateMyProfile cho người dùng tự cập nhật");
    }

    @Override
    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 3)
    public UserProfileResponse updateMyProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        // Update full name
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }

        // Update phone (check uniqueness if changed)
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String newPhone = request.getPhone().trim();
            String currentPhone = user.getPhone();
            if (!newPhone.equals(currentPhone)) {
                // Check if phone is already taken by another user
                userRepository.findByPhone(newPhone).ifPresent(existingUser -> {
                    if (!existingUser.getUserId().equals(user.getUserId())) {
                        throw BaseDomainException.of("user.conflict.phone-exists");
                    }
                });
                user.setPhone(newPhone);
            }
        }

        // Update student ID (check uniqueness if changed)
        if (request.getStudentId() != null && !request.getStudentId().trim().isEmpty()) {
            String newStudentId = request.getStudentId().trim();
            String currentStudentId = user.getStudentId();
            if (!newStudentId.equals(currentStudentId)) {
                // Check if student ID is already taken by another user
                if (userRepository.existsByStudentId(newStudentId)) {
                    throw BaseDomainException.of("user.conflict.student-id-exists");
                }
                user.setStudentId(newStudentId);
            }
        } else if (request.getStudentId() != null && request.getStudentId().trim().isEmpty()) {
            // Allow clearing student ID
            user.setStudentId(null);
        }

        // Update emergency contact (update primary contact if exists, or create new one)
        if (request.getEmergencyContact() != null && !request.getEmergencyContact().trim().isEmpty()) {
            String emergencyPhone = request.getEmergencyContact().trim();
            List<EmergencyContactResponse> existingContacts = emergencyContactService.getContacts(user);
            
            // Find primary contact
            EmergencyContactResponse primaryContact = existingContacts.stream()
                .filter(EmergencyContactResponse::getPrimary)
                .findFirst()
                .orElse(null);

            if (primaryContact != null) {
                // Update existing primary contact phone
                EmergencyContactRequest updateRequest = EmergencyContactRequest.builder()
                    .name(primaryContact.getName())
                    .phone(emergencyPhone)
                    .relationship(primaryContact.getRelationship())
                    .primary(true)
                    .build();
                emergencyContactService.updateContact(user, primaryContact.getContactId(), updateRequest);
            } else {
                // Create new primary emergency contact
                EmergencyContactRequest createRequest = EmergencyContactRequest.builder()
                    .name("Emergency Contact")
                    .phone(emergencyPhone)
                    .relationship("Emergency")
                    .primary(true)
                    .build();
                emergencyContactService.createContact(user, createRequest);
            }
        }

        userRepository.save(user);
        return getCurrentUserProfile(username);
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

        return MessageResponse.of("Cập nhật mật khẩu thành công");
    }

    @Override
    @Transactional
    public SwitchProfileResponse switchProfile(String username, SwitchProfileRequest request) {
        User user = userRepository.findByEmailWithProfiles(username)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email", "Không tìm thấy người dùng với email: " + username));

        String targetProfile = request.getTargetProfile();

        switch (UserProfileType.valueOf(targetProfile.toUpperCase())) {
            case DRIVER -> {
                if (user.getDriverProfile() == null) {
                    throw BaseDomainException.of("user.validation.profile-not-exists");
                }
//                if (!DriverProfileStatus.ACTIVE.equals(user.getDriverProfile().getStatus())) {
//                    throw BaseDomainException.of("user.validation.profile-not-active", "Driver profile is not active");
//                }
            }
            case RIDER -> {
                if (user.getRiderProfile() == null) {
                    throw BaseDomainException.of("user.validation.profile-not-exists");
                }
            }
            default -> throw ValidationException.of("Vai trò không hợp lệ: " + targetProfile);
        }

        authService.validateUserBeforeGrantingToken(user);

        var currentProfile = Optional.ofNullable(AuthServiceImpl.userContext.get(user.getUserId().toString()))
                .filter(Map.class::isInstance)
                .map(obj -> (Map<String, Object>) obj)
                .map(claims -> (String) claims.get("active_profile"))
                .orElse(null);

        if (currentProfile != null && !targetProfile.equalsIgnoreCase(currentProfile)) {
            if ("rider".equals(currentProfile)) {
                user.getRiderProfile().setStatus(RiderProfileStatus.INACTIVE);
            } else if ("driver".equals(currentProfile)) {
                user.getDriverProfile().setStatus(DriverProfileStatus.INACTIVE);
            }
        }

        if ("rider".equalsIgnoreCase(targetProfile)) {
            user.getRiderProfile().setStatus(RiderProfileStatus.ACTIVE);
        } else if ("driver".equalsIgnoreCase(targetProfile)) {
            user.getDriverProfile().setStatus(DriverProfileStatus.ACTIVE);
        }

        userRepository.save(user);

        Map<String, Object> claims = authService.buildTokenClaims(user, request.getTargetProfile());

        authService.setUserContext(user.getUserId(), claims);

        String accessToken = jwtService.generateToken(user.getEmail(), claims);

        return SwitchProfileResponse.builder()
                .accessToken(accessToken)
                .activeProfile(request.getTargetProfile())
                .build();
    }

    @Override
    @Transactional
    public VerificationResponse submitStudentVerification(String username, List<MultipartFile> documents) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));

        // Enforce email and phone verification before allowing student_id submission
        if (Boolean.FALSE.equals(user.getEmailVerified())) {
            throw ValidationException.of("Email phải được xác thực trước khi nộp mã sinh viên");
        }
        if (Boolean.FALSE.equals(user.getPhoneVerified())) {
            throw ValidationException.of("Số điện thoại phải được xác thực trước khi nộp mã sinh viên");
        }
        if(documents == null || documents.isEmpty()){
            throw new ValidationException("Cần ít nhất một tài liệu để tải lên");
        }
        if(verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(),VerificationType.STUDENT_ID,VerificationStatus.PENDING).isPresent()){
            throw new IllegalStateException("Yêu cầu xác thực sinh viên đã tồn tại");
        }
        try {
            if (verificationRepository.isUserVerifiedForType(user.getUserId(), VerificationType.STUDENT_ID)) {
                throw ConflictException.of("Xác thực sinh viên đã được phê duyệt");
            }

            List<CompletableFuture<String>> futuresList = documents.parallelStream()
                    .map(file -> {
                        try {
                            return fileUploadService.uploadFile(file);
                        } catch (Exception e) {
                            log.error("Failed to upload file: {}", e.getMessage());
                            CompletableFuture<String> failedFuture = new CompletableFuture<>();
                            failedFuture.completeExceptionally(
                                    new FileUploadException("Failed to upload file: " + file.getOriginalFilename()));
                            return failedFuture;
                        }
                    })
                    .collect(Collectors.toList());
            List<String> documentUrls = futuresList.stream()
                    .map(CompletableFuture:: join)
                    .collect(Collectors.toList());

            String documentUrlsCombined = String.join(",", documentUrls);

            Verification verification = Verification.builder()
                    .user(user)
                    .type(VerificationType.STUDENT_ID)
                    .status(VerificationStatus.PENDING)
                    .documentUrl(documentUrlsCombined)
                    .documentType(DocumentType.IMAGE)
                    .build();

            verification = verificationRepository.save(verification);

            return verificationMapper.mapToVerificationResponse(verification);
        } catch (Exception e) {
            throw new RuntimeException("Không thể tải lên tài liệu: " + e.getMessage());
        }
    }

    @Override
    public MessageResponse updateAvatar(String username, MultipartFile avatarFile) {
        User user = userRepository.findByEmail(username).orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        try {
            String profilePhotoUrl = fileUploadService.uploadFile(avatarFile).get();
            user.setProfilePhotoUrl(profilePhotoUrl);
            userRepository.save(user);
            return MessageResponse.builder()
                    .message("Tải lên ảnh đại diện thành công")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Không thể tải lên ảnh đại diện: " + e.getMessage());
        }
    }


    @Override
    @Transactional
    public VerificationResponse submitDriverLicense(String username, List<MultipartFile> documents) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        if (documents == null || documents.isEmpty()) {
            throw ValidationException.of("Cần ít nhất một tài liệu để tải lên");
        }
        if(verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(),VerificationType.DRIVER_LICENSE,VerificationStatus.PENDING).isPresent()){
            throw new IllegalStateException("Yêu cầu xác thực tài xế đã tồn tại");
        }
        boolean isValid = fptaiService.verifyDriverLicense(user, documents.get(0));
        if (!isValid) {
            throw ValidationException.of("Giấy phép lái xe không khớp với thông tin người dùng");
        }

        try {
            List<CompletableFuture<String>> futuresList = documents.parallelStream()
                    .map(file -> {
                        try {
                            return fileUploadService.uploadFile(file);
                        } catch (Exception e) {
                            log.error("Failed to upload file: {}", e.getMessage());
                            CompletableFuture<String> failedFuture = new CompletableFuture<>();
                            failedFuture.completeExceptionally(
                                    new FileUploadException("Failed to upload file: " + file.getOriginalFilename()));
                            return failedFuture;
                        }
                    })
                    .collect(Collectors.toList());
            List<String> documentUrls = futuresList.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            String documentUrlsCombined = String.join(",", documentUrls);

            Verification verification = Verification.builder()
                    .user(user)
                    .type(VerificationType.DRIVER_LICENSE)
                    .status(VerificationStatus.PENDING)
                    .documentUrl(documentUrlsCombined)
                    .documentType(DocumentType.IMAGE)
                    .build();

            verification = verificationRepository.save(verification);
            return verificationMapper.mapToVerificationResponse(verification);
        } catch (Exception e) {
            throw new RuntimeException("Không thể tải lên giấy phép lái xe: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public VerificationResponse submitDriverDocuments(String username, List<MultipartFile> documents) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        if (documents == null || documents.isEmpty()) {
            throw ValidationException.of("Cần ít nhất một tài liệu để tải lên");
        }
        if(verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(),VerificationType.DRIVER_DOCUMENTS,VerificationStatus.PENDING).isPresent()){
            throw new IllegalStateException("Yêu cầu xác thực tài xế đã tồn tại");
        }
        try {
            List<CompletableFuture<String>> futuresList = documents.parallelStream()
                    .map(file -> {
                        try {
                            return fileUploadService.uploadFile(file);
                        } catch (Exception e) {
                            log.error("Failed to upload file: {}", e.getMessage());
                            CompletableFuture<String> failedFuture = new CompletableFuture<>();
                            failedFuture.completeExceptionally(
                                    new FileUploadException("Failed to upload file: " + file.getOriginalFilename()));
                            return failedFuture;
                        }
                    })
                    .collect(Collectors.toList());
            List<String> documentUrls = futuresList.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            String documentUrlsCombined = String.join(",", documentUrls);

            Verification verification = Verification.builder()
                    .user(user)
                    .type(VerificationType.DRIVER_DOCUMENTS)
                    .status(VerificationStatus.PENDING)
                    .documentUrl(documentUrlsCombined)
                    .documentType(DocumentType.IMAGE)
                    .build();

            verification = verificationRepository.save(verification);
            return verificationMapper.mapToVerificationResponse(verification);
        } catch (Exception e) {
            throw new RuntimeException("Không thể tải lên tài liệu tài xế: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public VerificationResponse submitVehicleRegistration(String username, List<MultipartFile> documents) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> NotFoundException.userNotFound(username));
        if (documents == null || documents.isEmpty()) {
            throw ValidationException.of("Cần ít nhất một tài liệu để tải lên");
        }
        if(verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(),VerificationType.VEHICLE_REGISTRATION,VerificationStatus.PENDING).isPresent()){
            throw new IllegalStateException("Yêu cầu xác thực tài xế đã tồn tại");
        }
        
        // 验证车辆登记证的真实性
        boolean isValid = fptaiService.verifyVehicleRegistration(documents.get(0));
        if (!isValid) {
            throw ValidationException.of("Đăng ký xe không hợp lệ hoặc không phải ảnh chứng từ thật. Vui lòng tải lên ảnh đăng ký xe thật với độ phân giải tối thiểu 800x600 và kích thước tối thiểu 50KB");
        }
        
        try {
            List<CompletableFuture<String>> futuresList = documents.parallelStream()
                    .map(file -> {
                        try {
                            return fileUploadService.uploadFile(file);
                        } catch (Exception e) {
                            log.error("Failed to upload file: {}", e.getMessage());
                            CompletableFuture<String> failedFuture = new CompletableFuture<>();
                            failedFuture.completeExceptionally(
                                    new FileUploadException("Failed to upload file: " + file.getOriginalFilename()));
                            return failedFuture;
                        }
                    })
                    .collect(Collectors.toList());
            List<String> documentUrls = futuresList.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            String documentUrlsCombined = String.join(",", documentUrls);

            Verification verification = Verification.builder()
                    .user(user)
                    .type(VerificationType.VEHICLE_REGISTRATION)
                    .status(VerificationStatus.PENDING)
                    .documentUrl(documentUrlsCombined)
                    .documentType(DocumentType.IMAGE)
                    .build();

            verification = verificationRepository.save(verification);
            return verificationMapper.mapToVerificationResponse(verification);
        } catch (Exception e) {
            throw new RuntimeException("Không thể tải lên đăng ký xe: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> userPage = userRepository.findAll(pageable);

        List<UserResponse> userResponses = userPage.getContent().stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList());

        return PageResponse.<UserResponse>builder()
                .data(userResponses)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(userPage.getNumber() + 1)
                        .pageSize(userPage.getSize())
                        .totalPages(userPage.getTotalPages())
                        .totalRecords(userPage.getTotalElements())
                        .build())
                .build();
    }

    @Override
    @Transactional
    public void setDriverStatus(String username, boolean isActive) {
        User user = userRepository.findByEmailWithProfiles(username)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email", "Không tìm thấy người dùng với email: " + username));

        if (user.getDriverProfile() == null) {
            throw ValidationException.of("Người dùng không có hồ sơ tài xế");
        }

        DriverProfile driverProfile = user.getDriverProfile();

        DriverProfileStatus newStatus = isActive ? DriverProfileStatus.ACTIVE : DriverProfileStatus.INACTIVE;
        DriverProfileStatus currentStatus = driverProfile.getStatus();

        if (currentStatus != newStatus) {
            driverProfile.setStatus(newStatus);
            driverProfileRepository.save(driverProfile);

            log.info("Driver status updated for user {} from {} to {}",
                user.getUserId(), currentStatus, newStatus);
        }
    }

}
