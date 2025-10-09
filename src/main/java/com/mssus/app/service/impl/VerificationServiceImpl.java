package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.Verification;
import com.mssus.app.entity.User;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.mapper.VerificationMapper;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RiderProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.mapstruct.control.MappingControl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    private final VerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final VerificationMapper verificationMapper;
    private final RiderProfileRepository riderProfileRepository;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public StudentVerificationResponse getStudentVerificationById(Integer userId) {
        Verification verification = verificationRepository.findByUserIdAndTypeAndStatus(userId, VerificationType.STUDENT_ID, VerificationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException("Student verification not found for user ID: " + userId));
        return verificationMapper.mapToStudentVerificationResponse(verification);
    }

    @Override
    @Transactional
    public BulkOperationResponse bulkApproveVerifications(String admin, BulkApprovalRequest request) {
        List<Integer> successfulIds = new ArrayList<>();
        List<BulkOperationResponse.FailedItem> failedItems = new ArrayList<>();

        for (Integer verificationId : request.getVerificationIds()) {
            try {
                Verification verification = verificationRepository.findById(verificationId)
                        .orElseThrow(() -> new NotFoundException("Verification not found"));

                User verifiedBy = userRepository.findByEmail(admin)
                        .orElseThrow(() -> new NotFoundException("Admin user not found"));

                if (!VerificationStatus.PENDING.equals(verification.getStatus())) {
                    failedItems.add(BulkOperationResponse.FailedItem.builder()
                            .id(verificationId)
                            .reason("Not a student verification or Verification not in pending status")
                            .build());
                    continue;
                }

                verification.setStatus(VerificationStatus.APPROVED);
                verification.setVerifiedBy(verifiedBy);
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

    @Override
    @Transactional(readOnly = true)
    public DriverKycResponse getDriverKycById(Integer driverId) {
        DriverProfile driver = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));
        return mapToDriverKycResponse(driver);
    }

    @Override
    public MessageResponse approveDriverVehicle(String admin, VerificationDecisionRequest request) {
        return null;
    }

    //done
    @Override
    @Transactional
    public MessageResponse rejectVerification(String admin, VerificationDecisionRequest request) {
        if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
            throw new ValidationException("Rejection reason is required");
        }
        Integer userId = request.getUserId();
        VerificationType typeStr = VerificationType.valueOf(request.getVerificationType());
        Verification verification = verificationRepository.findByUserId(userId).orElseThrow(
                () -> new NotFoundException("Verification not found for user ID: " + userId)
        );

        User verifiedBy = userRepository.findByEmail(admin)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));

        if (verification == null) {
            throw new NotFoundException("User verifications not found for driver ID: " + userId);
        }

        if (VerificationStatus.PENDING.equals(verification.getStatus())) {
            verification.setStatus(VerificationStatus.REJECTED);
            verification.setRejectionReason(request.getRejectionReason());
            verification.setVerifiedBy(verifiedBy);
            verification.setVerifiedAt(LocalDateTime.now());

            if (request.getNotes() != null) {
                verification.setMetadata(request.getNotes());
            }

            verificationRepository.save(verification);
        }

        User user = verification.getUser();

        if (user.getRiderProfile() != null && typeStr == VerificationType.STUDENT_ID) {
            RiderProfile rider = user.getRiderProfile();
            rider.setStatus(RiderProfileStatus.SUSPENDED);
            riderProfileRepository.save(rider);
        } else if (user.getDriverProfile() != null && (typeStr == VerificationType.DRIVER_DOCUMENTS || typeStr == VerificationType.DRIVER_LICENSE || typeStr == VerificationType.VEHICLE_REGISTRATION)) {
            DriverProfile driver = user.getDriverProfile();
            driver.setStatus(DriverProfileStatus.REJECTED);
            driverProfileRepository.save(driver);

        }

        return MessageResponse.builder()
                .message("User verification rejected")
                .build();
    }

    @Override
    @Transactional
    public MessageResponse updateBackgroundCheck(String admin, Integer driverId, BackgroundCheckRequest request) {
        DriverProfile driver = driverProfileRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found with ID: " + driverId));

        // Find or create background check verification
        Verification bgCheck = verificationRepository.findByUserIdAndTypeAndStatus(driverId, VerificationType.BACKGROUND_CHECK, VerificationStatus.PENDING)
                .orElse(Verification.builder()
                        .user(driver.getUser())
                        .type(VerificationType.BACKGROUND_CHECK)
                        .status(VerificationStatus.PENDING)
                        .build());

        User verifiedBy = userRepository.findByEmail(admin).orElseThrow(
                () -> new NotFoundException("Admin user not found")
        );
        if ("failed".equals(request.getResult())) {
            bgCheck.setStatus(VerificationStatus.REJECTED);
        } else if ("passed".equals(request.getResult())) {
            bgCheck.setStatus(VerificationStatus.APPROVED);
        }

        bgCheck.setVerifiedBy(verifiedBy);
        bgCheck.setVerifiedAt(LocalDateTime.now());
        bgCheck.setMetadata("Background check details: " + request.getDetails());

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
        Long totalDrivers = driverProfileRepository.count();
        Long pendingDocs = verificationRepository.countByTypeAndStatus(VerificationType.DRIVER_DOCUMENTS, VerificationStatus.PENDING);
        Long pendingLicenses = verificationRepository.countByTypeAndStatus(VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING);
        Long pendingVehicles = verificationRepository.countByTypeAndStatus(VerificationType.VEHICLE_REGISTRATION, VerificationStatus.PENDING);
        Long pendingBgChecks = verificationRepository.countByTypeAndStatus(VerificationType.BACKGROUND_CHECK, VerificationStatus.PENDING);
        Long pendingVerifications = pendingDocs + pendingLicenses + pendingVehicles + pendingBgChecks;
        Long approvedDrivers = driverProfileRepository.countByStatus(DriverProfileStatus.ACTIVE);
        Long rejectedVerifications = verificationRepository.countByStatus(VerificationStatus.REJECTED);

        Double completionRate = totalDrivers > 0 ? (approvedDrivers.doubleValue() / totalDrivers.doubleValue()) * 100 : 0.0;

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VerificationResponse> getAllVerifications(Pageable pageable) {
        Page<Verification> verificationsPage = verificationRepository.findAll(pageable);
        List<VerificationResponse> verifications = verificationsPage.getContent().stream()
                .map(verificationMapper::mapToVerificationResponse)
                .toList();

        return buildPageResponse(verificationsPage, verifications);
    }

    //done
    @Override
    @Transactional
    public MessageResponse approveVerification(String admin, VerificationDecisionRequest request) {
        User user = userRepository.findById(request.getUserId()).orElseThrow(
                () -> new NotFoundException("User not found")
        );
        VerificationType typeStr = VerificationType.valueOf(request.getVerificationType());
        Verification verification = verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(), typeStr, VerificationStatus.PENDING)
                .orElseThrow(() -> new NotFoundException(typeStr + " verification not found for user ID: " + user.getUserId()));

        User verifiedBy = userRepository.findByEmail(admin).orElseThrow(
                () -> new NotFoundException("Admin user not found")
        );
        verification.setStatus(VerificationStatus.APPROVED);
        verification.setVerifiedBy(verifiedBy);
        verification.setVerifiedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            verification.setMetadata(request.getNotes());
        }

        if(typeStr == VerificationType.STUDENT_ID && user.getRiderProfile() != null){
            RiderProfile rider = user.getRiderProfile();
            rider.setStatus(RiderProfileStatus.ACTIVE);
            riderProfileRepository.save(rider);
        } else if ((isDriverVerification(typeStr))
                && user.getDriverProfile() != null) {
            checkAndActivateDriverProfile(user);
        }


        verificationRepository.save(verification);
        return MessageResponse.builder()
                .message(typeStr.name().toLowerCase().replace("_", " ") + " approved successfully")
                .build();
    }


    //Helper method
    private boolean isDriverVerification(VerificationType type) {
        return type == VerificationType.DRIVER_DOCUMENTS ||
                type == VerificationType.DRIVER_LICENSE ||
                type == VerificationType.VEHICLE_REGISTRATION ||
                type == VerificationType.BACKGROUND_CHECK;
    }
    private void checkAndActivateDriverProfile(User user) {
        List<VerificationType> requiredTypes = Arrays.asList(
                VerificationType.DRIVER_LICENSE,
                VerificationType.DRIVER_DOCUMENTS,
                VerificationType.VEHICLE_REGISTRATION
                // BACKGROUND_CHECK
        );

        List<Verification> userVerifications = verificationRepository.findByListUserId(user.getUserId());

        Map<VerificationType, VerificationStatus> verificationMap = userVerifications.stream()
                .collect(Collectors.toMap(
                        Verification::getType,
                        Verification::getStatus,
                        (existing, replacement) -> existing
                ));

        boolean allRequiredApproved = requiredTypes.stream()
                .allMatch(type -> verificationMap.get(type) == VerificationStatus.APPROVED);

        DriverProfile driver = user.getDriverProfile();

        if (allRequiredApproved) {
            driver.setStatus(DriverProfileStatus.ACTIVE);
            driverProfileRepository.save(driver);

            emailService.notifyDriverActivated(user);
        } else {
            boolean hasRejected = userVerifications.stream()
                    .filter(v -> requiredTypes.contains(v.getType()))
                    .anyMatch(v -> v.getStatus() == VerificationStatus.REJECTED);

            if (hasRejected && driver.getStatus() == DriverProfileStatus.ACTIVE) {
                driver.setStatus(DriverProfileStatus.SUSPENDED);
                driverProfileRepository.save(driver);
                emailService.notifyDriverSuspended(user);
            }
        }
    }

    private DriverKycResponse mapToDriverKycResponse(DriverProfile driver) {
        List<Verification> verifications = verificationRepository.findByListUserId(driver.getDriverId());
        List<DriverKycResponse.VerificationInfo> verificationInfos = verifications.stream()
                .map(v -> DriverKycResponse.VerificationInfo.builder()
                        .verificationId(v.getVerificationId())
                        .type(v.getType().name())
                        .status(v.getStatus().name())
                        .documentUrl(v.getDocumentUrl())
                        .documentType(v.getDocumentType().name())
                        .rejectionReason(v.getRejectionReason())
                        .verifiedBy(v.getVerifiedBy() != null ?
                                v.getVerifiedBy().getUserId().toString() : null)
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