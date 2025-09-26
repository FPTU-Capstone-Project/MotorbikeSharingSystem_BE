package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.AdminProfile;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Verification;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.repository.AdminProfileRepository;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private final VerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final AdminProfileRepository adminProfileRepository;

    // Student verification methods
    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentVerificationResponse> getPendingStudentVerifications(Pageable pageable) {
        Page<Verification> verificationsPage = verificationRepository.findByTypeAndStatus(VerificationType.STUDENT_ID, VerificationStatus.PENDING, pageable);
        List<StudentVerificationResponse> students = verificationsPage.getContent().stream()
                .map(this::mapToStudentVerificationResponse)
                .toList();

        return buildPageResponse(verificationsPage, students);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentVerificationResponse getStudentVerificationById(Integer userId) {
        Verification verification = verificationRepository.findByUserIdAndTypeAndStatus(userId, VerificationType.STUDENT_ID, VerificationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Student verification not found for user ID: " + userId));
        return mapToStudentVerificationResponse(verification);
    }

    @Override
    @Transactional
    public MessageResponse approveStudentVerification(Integer userId, VerificationDecisionRequest request) {
        Verification verification = verificationRepository.findByUserIdAndTypeAndStatus(userId, VerificationType.STUDENT_ID, VerificationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Student verification not found for user ID: " + userId));

        AdminProfile admin = getCurrentAdmin();
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setVerifiedBy(admin);
        verification.setVerifiedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            verification.setMetadata(request.getNotes());
        }

        verificationRepository.save(verification);
        return MessageResponse.builder()
                .message("Student verification approved successfully")
                .build();
    }

    @Override
    @Transactional
    public MessageResponse rejectStudentVerification(Integer userId, VerificationDecisionRequest request) {
        if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
            throw ValidationException.of("Rejection reason is required");
        }

        Verification verification = verificationRepository.findByUserIdAndTypeAndStatus(userId, VerificationType.STUDENT_ID, VerificationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Student verification not found for user ID: " + userId));

        AdminProfile admin = getCurrentAdmin();
        verification.setStatus(VerificationStatus.REJECTED);
        verification.setRejectionReason(request.getRejectionReason());
        verification.setVerifiedBy(admin);
        verification.setVerifiedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            verification.setMetadata(request.getNotes());
        }

        verificationRepository.save(verification);
        return MessageResponse.builder()
                .message("Student verification rejected")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentVerificationResponse> getStudentVerificationHistory(Pageable pageable) {
        Page<Verification> verificationsPage = verificationRepository.findByTypeAndStatus(VerificationType.STUDENT_ID, VerificationStatus.APPROVED, pageable);
        List<StudentVerificationResponse> students = verificationsPage.getContent().stream()
                .map(this::mapToStudentVerificationResponse)
                .toList();

        return buildPageResponse(verificationsPage, students);
    }

    @Override
    @Transactional
    public BulkOperationResponse bulkApproveStudentVerifications(BulkApprovalRequest request) {
        AdminProfile admin = getCurrentAdmin();
        List<Integer> successfulIds = new ArrayList<>();
        List<BulkOperationResponse.FailedItem> failedItems = new ArrayList<>();

        for (Integer verificationId : request.getVerificationIds()) {
            try {
                Verification verification = verificationRepository.findById(verificationId)
                        .orElseThrow(() -> new NotFoundException("Verification not found"));

                if (!VerificationType.STUDENT_ID.equals(verification.getType())) {
                    failedItems.add(BulkOperationResponse.FailedItem.builder()
                            .id(verificationId)
                            .reason("Not a student verification")
                            .build());
                    continue;
                }

                if (!VerificationStatus.PENDING.equals(verification.getStatus())) {
                    failedItems.add(BulkOperationResponse.FailedItem.builder()
                            .id(verificationId)
                            .reason("Verification not in pending status")
                            .build());
                    continue;
                }

                verification.setStatus(VerificationStatus.APPROVED);
                verification.setVerifiedBy(admin);
                verification.setVerifiedAt(LocalDateTime.now());

                if (request.getNotes() != null) {
                    verification.setMetadata(request.getNotes());
                }

                verificationRepository.save(verification);
                successfulIds.add(verificationId);

            } catch (Exception e) {
                failedItems.add(BulkOperationResponse.FailedItem.builder()
                        .id(verificationId)
                        .reason(e.getMessage())
                        .build());
            }
        }

        return BulkOperationResponse.builder()
                .totalRequested(request.getVerificationIds().size())
                .successfulCount(successfulIds.size())
                .failedCount(failedItems.size())
                .successfulIds(successfulIds)
                .failedItems(failedItems)
                .message("Bulk approval completed")
                .build();
    }

    // Driver verification methods
    @Override
    @Transactional(readOnly = true)
    public PageResponse<DriverKycResponse> getPendingDriverVerifications(Pageable pageable) {
        Page<DriverProfile> driversPage = driverProfileRepository.findByStatus(DriverProfileStatus.PENDING, pageable);
        List<DriverKycResponse> drivers = driversPage.getContent().stream()
                .map(this::mapToDriverKycResponse)
                .toList();

        return buildPageResponse(driversPage, drivers);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverKycResponse getDriverKycById(Integer driverId) {
        DriverProfile driver = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));
        return mapToDriverKycResponse(driver);
    }

    @Override
    @Transactional
    public MessageResponse approveDriverDocuments(Integer driverId, VerificationDecisionRequest request) {
        return approveDriverVerificationType(driverId, VerificationType.DRIVER_DOCUMENTS, request);
    }

    @Override
    @Transactional
    public MessageResponse approveDriverLicense(Integer driverId, VerificationDecisionRequest request) {
        MessageResponse response = approveDriverVerificationType(driverId, VerificationType.DRIVER_LICENSE, request);

        // Update driver profile license verification
        DriverProfile driver = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));
        driver.setLicenseVerifiedAt(LocalDateTime.now());
        driverProfileRepository.save(driver);

        return response;
    }

    @Override
    @Transactional
    public MessageResponse approveDriverVehicle(Integer driverId, VerificationDecisionRequest request) {
        return approveDriverVerificationType(driverId, VerificationType.VEHICLE_REGISTRATION, request);
    }

    @Override
    @Transactional
    public MessageResponse rejectDriverVerification(Integer driverId, VerificationDecisionRequest request) {
        if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
            throw ValidationException.of("Rejection reason is required");
        }

        List<Verification> verifications = verificationRepository.findByUserId(driverId);
        if (verifications.isEmpty()) {
            throw NotFoundException.resourceNotFound("Driver verifications", "driver ID " + driverId);
        }

        AdminProfile admin = getCurrentAdmin();

        // Reject all pending verifications for this driver
        for (Verification verification : verifications) {
            if (VerificationStatus.PENDING.equals(verification.getStatus())) {
                verification.setStatus(VerificationStatus.REJECTED);
                verification.setRejectionReason(request.getRejectionReason());
                verification.setVerifiedBy(admin);
                verification.setVerifiedAt(LocalDateTime.now());

                if (request.getNotes() != null) {
                    verification.setMetadata(request.getNotes());
                }

                verificationRepository.save(verification);
            }
        }

        // Update driver profile status
        DriverProfile driver = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));
        driver.setStatus(DriverProfileStatus.REJECTED);
        driverProfileRepository.save(driver);

        return MessageResponse.builder()
                .message("Driver verification rejected")
                .build();
    }

    @Override
    @Transactional
    public MessageResponse updateBackgroundCheck(Integer driverId, BackgroundCheckRequest request) {
        DriverProfile driver = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));

        // Find or create background check verification
        Verification bgCheck = verificationRepository.findByUserIdAndTypeAndStatus(driverId, VerificationType.BACKGROUND_CHECK, VerificationStatus.PENDING)
                .orElse(Verification.builder()
                        .user(driver.getUser())
                        .type(VerificationType.BACKGROUND_CHECK)
                        .status(VerificationStatus.PENDING)
                        .build());

        AdminProfile admin = getCurrentAdmin();
        bgCheck.setStatus(VerificationStatus.valueOf(request.getResult()));
        bgCheck.setVerifiedBy(admin);
        bgCheck.setVerifiedAt(LocalDateTime.now());
        bgCheck.setMetadata("{\"details\":\"" + request.getDetails() + "\",\"conductedBy\":\"" + request.getConductedBy() + "\"}");

        if ("failed".equals(request.getResult())) {
            bgCheck.setRejectionReason("Background check failed: " + request.getDetails());
            driver.setStatus(DriverProfileStatus.REJECTED);
        } else if ("passed".equals(request.getResult())) {
            // Check if all other verifications are approved to activate driver
            boolean allApproved = verificationRepository.findByUserId(driverId).stream()
                    .filter(v -> !VerificationType.BACKGROUND_CHECK.equals(v.getType()))
                    .allMatch(v -> VerificationStatus.APPROVED.equals(v.getStatus()));

            if (allApproved) {
                driver.setStatus(DriverProfileStatus.ACTIVE);
            }
        }

        verificationRepository.save(bgCheck);
        driverProfileRepository.save(driver);

        return MessageResponse.builder()
                .message("Background check updated successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DriverStatsResponse getDriverVerificationStats() {
        long totalDrivers = driverProfileRepository.count();
        Long pendingDocs = verificationRepository.countByTypeAndStatus(VerificationType.DRIVER_DOCUMENTS, VerificationStatus.PENDING);
        Long pendingLicenses = verificationRepository.countByTypeAndStatus(VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING);
        Long pendingVehicles = verificationRepository.countByTypeAndStatus(VerificationType.VEHICLE_REGISTRATION, VerificationStatus.PENDING);
        Long pendingBgChecks = verificationRepository.countByTypeAndStatus(VerificationType.BACKGROUND_CHECK, VerificationStatus.PENDING);
        Long pendingVerifications = pendingDocs + pendingLicenses + pendingVehicles + pendingBgChecks;
        Long approvedDrivers = driverProfileRepository.countByStatus(DriverProfileStatus.ACTIVE);
        Long rejectedVerifications = verificationRepository.countByStatus(VerificationStatus.REJECTED);

        double completionRate = totalDrivers > 0 ? (approvedDrivers.doubleValue() / (double) totalDrivers) * 100 : 0.0;

        return DriverStatsResponse.builder()
                .totalDrivers(totalDrivers)
                .pendingVerifications(pendingVerifications)
                .pendingDocuments(pendingDocs)
                .pendingLicenses(pendingLicenses)
                .pendingVehicles(pendingVehicles)
                .pendingBackgroundChecks(pendingBgChecks)
                .approvedDrivers(approvedDrivers)
                .rejectedVerifications(rejectedVerifications)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .build();
    }

    // Common verification methods
    @Override
    @Transactional(readOnly = true)
    public VerificationResponse getVerificationById(Integer verificationId) {
        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found with ID: " + verificationId));
        return mapToVerificationResponse(verification);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VerificationResponse> getAllVerifications(Pageable pageable) {
        Page<Verification> verificationsPage = verificationRepository.findAll(pageable);
        List<VerificationResponse> verifications = verificationsPage.getContent().stream()
                .map(this::mapToVerificationResponse)
                .toList();

        return buildPageResponse(verificationsPage, verifications);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VerificationResponse> getAllPendingVerifications(Pageable pageable) {
        Page<Verification> verificationsPage = verificationRepository.findByStatus(VerificationStatus.PENDING, pageable);
        List<VerificationResponse> verifications = verificationsPage.getContent().stream()
                .map(this::mapToVerificationResponse)
                .toList();

        return buildPageResponse(verificationsPage, verifications);
    }

    @Override
    @Transactional
    public VerificationResponse approveVerification(Integer verificationId) {
        Verification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new NotFoundException("Verification not found with ID: " + verificationId));

        AdminProfile admin = getCurrentAdmin();
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setVerifiedBy(admin);
        verification.setVerifiedAt(LocalDateTime.now());

        verificationRepository.save(verification);
        return mapToVerificationResponse(verification);
    }

    // Helper methods
    private MessageResponse approveDriverVerificationType(Integer driverId, VerificationType type, VerificationDecisionRequest request) {
        Verification verification = verificationRepository.findByUserIdAndTypeAndStatus(driverId, type, VerificationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException(type + " verification not found for driver ID: " + driverId));

        AdminProfile admin = getCurrentAdmin();
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setVerifiedBy(admin);
        verification.setVerifiedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            verification.setMetadata(request.getNotes());
        }

        verificationRepository.save(verification);

        // Check if all verifications are approved to activate driver
        boolean allApproved = verificationRepository.findByUserId(driverId).stream()
                .allMatch(v -> VerificationStatus.APPROVED.equals(v.getStatus()) || VerificationType.BACKGROUND_CHECK.equals(v.getType()));

        if (allApproved) {
            DriverProfile driver = driverProfileRepository.findById(driverId)
                    .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));
            driver.setStatus(DriverProfileStatus.ACTIVE);
            driverProfileRepository.save(driver);
        }

        return MessageResponse.builder()
                .message(type.name().replace("_", " ") + " approved successfully")
                .build();
    }

    private AdminProfile getCurrentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Current user not found"));

        return adminProfileRepository.findById(user.getUserId())
                .orElseThrow(() -> new NotFoundException("Admin profile not found"));
    }

    private StudentVerificationResponse mapToStudentVerificationResponse(Verification verification) {
        User user = verification.getUser();
        return StudentVerificationResponse.builder()
                .verificationId(verification.getVerificationId())
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .studentId(user.getStudentId())
                .status(verification.getStatus().name())
                .documentUrl(verification.getDocumentUrl())
                .documentType(verification.getDocumentType().name())
                .rejectionReason(verification.getRejectionReason())
                .verifiedBy(verification.getVerifiedBy() != null ?
                           verification.getVerifiedBy().getUser().getFullName() : null)
                .verifiedAt(verification.getVerifiedAt())
                .createdAt(verification.getCreatedAt())
                .build();
    }

    private DriverKycResponse mapToDriverKycResponse(DriverProfile driver) {
        List<Verification> verifications = verificationRepository.findByUserId(driver.getDriverId());
        List<DriverKycResponse.VerificationInfo> verificationInfos = verifications.stream()
                .map(v -> DriverKycResponse.VerificationInfo.builder()
                        .verificationId(v.getVerificationId())
                        .type(v.getType().name())
                        .status(v.getStatus().name())
                        .documentUrl(v.getDocumentUrl())
                        .documentType(v.getDocumentType().name())
                        .rejectionReason(v.getRejectionReason())
                        .verifiedBy(v.getVerifiedBy() != null ?
                                   v.getVerifiedBy().getUser().getFullName() : null)
                        .verifiedAt(v.getVerifiedAt())
                        .createdAt(v.getCreatedAt())
                        .build())
                .toList();

        User user = driver.getUser();
        return DriverKycResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .licenseNumber(driver.getLicenseNumber())
                .driverStatus(driver.getStatus().name())
                .verifications(verificationInfos)
                .createdAt(driver.getCreatedAt())
                .build();
    }

    private VerificationResponse mapToVerificationResponse(Verification verification) {
        return VerificationResponse.builder()
                .verificationId(verification.getVerificationId())
                .userId(verification.getUser().getUserId())
                .type(verification.getType().name())
                .status(verification.getStatus().name())
                .documentUrl(verification.getDocumentUrl())
                .documentType(verification.getDocumentType().name())
                .rejectionReason(verification.getRejectionReason())
                .verifiedBy(verification.getVerifiedBy() != null ?
                           verification.getVerifiedBy().getUser().getFullName() : null)
                .verifiedAt(verification.getVerifiedAt())
                .expiresAt(verification.getExpiresAt())
                .metadata(verification.getMetadata())
                .createdAt(verification.getCreatedAt())
                .build();
    }

    private <T> PageResponse<T> buildPageResponse(Page<?> page, List<T> content) {
        return PageResponse.<T>builder()
                .data(content)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(page.getNumber() + 1)
                        .pageSize(page.getSize())
                        .totalPages(page.getTotalPages())
                        .totalRecords(page.getTotalElements())
                        .build())
                .build();
    }
}
