package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.VerificationStatus;
import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.dto.request.BackgroundCheckRequest;
import com.mssus.app.dto.request.BulkApprovalRequest;
import com.mssus.app.dto.request.VerificationDecisionRequest;
import com.mssus.app.dto.response.BulkOperationResponse;
import com.mssus.app.dto.response.DriverStatsResponse;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.StudentVerificationResponse;
import com.mssus.app.dto.response.VerificationResponse;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Verification;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.mapper.VerificationMapper;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.repository.RiderProfileRepository;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.domain.verification.VerificationOutcomeHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2025, 1, 1, 9, 30);

    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DriverProfileRepository driverProfileRepository;
    @Mock
    private VerificationMapper verificationMapper;
    @Mock
    private RiderProfileRepository riderProfileRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private VerificationOutcomeHandler verificationOutcomeHandler;

    @InjectMocks
    private VerificationServiceImpl verificationService;

    @Test
    void should_returnStudentVerification_when_latestExists() {
        // Arrange
        Integer userId = 42;
        User user = createUser(userId);
        Verification older = buildVerification(10, user, VerificationType.STUDENT_ID, VerificationStatus.PENDING, FIXED_NOW.minusDays(2));
        Verification latest = buildVerification(11, user, VerificationType.STUDENT_ID, VerificationStatus.APPROVED, FIXED_NOW.minusHours(1));
        StudentVerificationResponse expectedResponse = StudentVerificationResponse.builder()
                .userId(userId)
                .build();

        when(verificationRepository.findByUserIdAndType(userId, VerificationType.STUDENT_ID))
                .thenReturn(List.of(older, latest));
        when(verificationMapper.mapToStudentVerificationResponse(any(Verification.class)))
                .thenReturn(expectedResponse);

        // Act
        StudentVerificationResponse result = verificationService.getStudentVerificationById(userId);

        // Assert
        assertThat(result).isSameAs(expectedResponse);

        ArgumentCaptor<Verification> verificationCaptor = ArgumentCaptor.forClass(Verification.class);
        verify(verificationMapper).mapToStudentVerificationResponse(verificationCaptor.capture());
        assertThat(verificationCaptor.getValue()).isSameAs(latest);

        verify(verificationRepository).findByUserIdAndType(userId, VerificationType.STUDENT_ID);
        verifyNoMoreInteractions(verificationRepository, verificationMapper);
    }

    @Test
    void should_throwNotFound_when_studentVerificationMissing() {
        // Arrange
        Integer userId = 7;
        when(verificationRepository.findByUserIdAndType(userId, VerificationType.STUDENT_ID))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> verificationService.getStudentVerificationById(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Student verification not found");

        verify(verificationRepository).findByUserIdAndType(userId, VerificationType.STUDENT_ID);
        verifyNoInteractions(verificationMapper);
    }

    @Test
    void should_returnSummary_when_bulkApproveHasMixedResults() {
        // Arrange
        BulkApprovalRequest request = BulkApprovalRequest.builder()
                .verificationIds(Arrays.asList(100, 100, 200))
                .notes("Batch approval")
                .build();
        User admin = createUser(900);
        User riderUser = createUser(1);
        RiderProfile riderProfile = createRiderProfile(riderUser, RiderProfileStatus.PENDING);
        riderProfile.setActivatedAt(null);
        riderUser.setRiderProfile(riderProfile);

        Verification pendingVerification = buildVerification(100, riderUser, VerificationType.STUDENT_ID, VerificationStatus.PENDING, FIXED_NOW.minusDays(3));
        pendingVerification.setMetadata(null);
        Verification alreadyProcessed = buildVerification(200, riderUser, VerificationType.STUDENT_ID, VerificationStatus.APPROVED, FIXED_NOW.minusDays(4));

        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
        when(verificationRepository.findById(100)).thenReturn(Optional.of(pendingVerification));
        when(verificationRepository.findById(200)).thenReturn(Optional.of(alreadyProcessed));

        LocalDateTime now = FIXED_NOW.plusMinutes(10);
        try (MockedStatic<LocalDateTime> mockedNow = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS);
             MockedStatic<CompletableFuture> mockedFuture = mockStatic(CompletableFuture.class, CALLS_REAL_METHODS)) {

            mockedNow.when(LocalDateTime::now).thenReturn(now);
            mockedFuture.when(() -> CompletableFuture.runAsync(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return CompletableFuture.completedFuture(null);
            });

            // Act
            BulkOperationResponse response = verificationService.bulkApproveVerifications(ADMIN_EMAIL, request);

            // Assert
            assertThat(response.getTotalRequested()).isEqualTo(2);
            assertThat(response.getSuccessfulCount()).isEqualTo(1);
            assertThat(response.getFailedCount()).isEqualTo(1);
            assertThat(response.getSuccessfulIds()).containsExactly(100);
            assertThat(response.getFailedItems()).singleElement().satisfies(item -> {
                assertThat(item.getId()).isEqualTo(200);
                assertThat(item.getReason()).contains("not in PENDING status");
            });
            assertThat(response.getMessage()).contains("Bulk approval completed");

            ArgumentCaptor<Verification> savedVerification = ArgumentCaptor.forClass(Verification.class);
            verify(verificationRepository).save(savedVerification.capture());
            Verification updatedVerification = savedVerification.getValue();
            assertThat(updatedVerification.getStatus()).isEqualTo(VerificationStatus.APPROVED);
            assertThat(updatedVerification.getVerifiedBy()).isEqualTo(admin);
            assertThat(updatedVerification.getVerifiedAt()).isEqualTo(now);
            assertThat(updatedVerification.getMetadata()).isEqualTo(request.getNotes());

            verify(userRepository).findByEmail(ADMIN_EMAIL);
            verify(verificationRepository).findById(100);
            verify(verificationRepository).findById(200);
            verify(verificationOutcomeHandler).handleApproval(pendingVerification);
            verify(emailService).notifyUserActivated(riderUser);
            verifyNoInteractions(riderProfileRepository);
            verifyNoMoreInteractions(verificationRepository, userRepository, verificationOutcomeHandler, emailService);
        }
    }

    @Test
    void should_throwValidationException_when_bulkApproveIdsEmpty() {
        // Arrange
        BulkApprovalRequest request = BulkApprovalRequest.builder()
                .verificationIds(Collections.emptyList())
                .build();

        // Act & Assert
        assertThatThrownBy(() -> verificationService.bulkApproveVerifications(ADMIN_EMAIL, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Verification IDs list cannot be empty");

        verifyNoInteractions(userRepository, verificationRepository, riderProfileRepository, emailService);
    }

    @ParameterizedTest
    @MethodSource("driverVerificationTypes")
    void should_delegateOutcomeHandler_when_driverVerificationApproved(VerificationType type) throws Exception {
        // Arrange
        Integer userId = 55;
        User user = createUser(userId);
        DriverProfile driverProfile = createDriverProfile(user, DriverProfileStatus.PENDING);
        user.setDriverProfile(driverProfile);
        Verification verification = buildVerification(301, user, type, VerificationStatus.PENDING, FIXED_NOW.minusDays(1));

        User admin = createUser(999);
        VerificationDecisionRequest request = VerificationDecisionRequest.builder()
                .userId(userId)
                .verificationType(type.name())
                .notes("Looks good")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdAndTypeAndStatus(userId, type, VerificationStatus.PENDING))
                .thenReturn(Optional.of(verification));
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        LocalDateTime now = FIXED_NOW.plusMinutes(5);
        try (MockedStatic<LocalDateTime> mockedNow = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedNow.when(LocalDateTime::now).thenReturn(now);

            // Act
            MessageResponse response = verificationService.approveVerification(ADMIN_EMAIL, request);

            // Assert
            assertThat(response.getMessage()).contains("approved successfully");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.APPROVED);
            assertThat(verification.getVerifiedBy()).isEqualTo(admin);
            assertThat(verification.getVerifiedAt()).isEqualTo(now);
            assertThat(verification.getMetadata()).isEqualTo(request.getNotes());

            verify(userRepository).findById(userId);
            verify(userRepository).findByEmail(ADMIN_EMAIL);
            verify(verificationRepository).findByUserIdAndTypeAndStatus(userId, type, VerificationStatus.PENDING);
            verify(verificationRepository).save(verification);
            verify(verificationOutcomeHandler).handleApproval(verification);
        }
    }

    private static Stream<VerificationType> driverVerificationTypes() {
        return Stream.of(
                VerificationType.DRIVER_LICENSE,
                VerificationType.DRIVER_DOCUMENTS,
                VerificationType.VEHICLE_REGISTRATION
        );
    }

    @Test
    void should_throwNotFound_when_driverVerificationMissing() {
        // Arrange
        Integer userId = 77;
        VerificationDecisionRequest request = VerificationDecisionRequest.builder()
                .userId(userId)
                .verificationType(VerificationType.DRIVER_LICENSE.name())
                .build();
        User user = createUser(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdAndTypeAndStatus(userId, VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> verificationService.approveVerification(ADMIN_EMAIL, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("verification not found");

        verify(userRepository).findById(userId);
        verify(verificationRepository).findByUserIdAndTypeAndStatus(userId, VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING);
        verifyNoMoreInteractions(userRepository, verificationRepository);
    }

    @Test
    void should_updateVerificationAndProfiles_when_rejectRequestValid() {
        // Arrange
        Integer userId = 88;
        User user = createUser(userId);
        DriverProfile driverProfile = createDriverProfile(user, DriverProfileStatus.PENDING);
        user.setDriverProfile(driverProfile);

        Verification verification = buildVerification(410, user, VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING, FIXED_NOW.minusDays(2));
        VerificationDecisionRequest request = VerificationDecisionRequest.builder()
                .userId(userId)
                .verificationType(VerificationType.DRIVER_LICENSE.name())
                .rejectionReason("Document blurred")
                .notes("Please resubmit")
                .build();
        User admin = createUser(901);

        when(verificationRepository.findByUserIdAndTypeAndStatus(userId, VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING))
                .thenReturn(Optional.of(verification));
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        LocalDateTime now = FIXED_NOW.plusMinutes(15);
        try (MockedStatic<LocalDateTime> mockedNow = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedNow.when(LocalDateTime::now).thenReturn(now);

            // Act
            MessageResponse response = verificationService.rejectVerification(ADMIN_EMAIL, request);

            // Assert
            assertThat(response.getMessage()).isEqualTo("User verification rejected");
            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.REJECTED);
            assertThat(verification.getVerifiedBy()).isEqualTo(admin);
            assertThat(verification.getVerifiedAt()).isEqualTo(now);
            assertThat(verification.getRejectionReason()).isEqualTo("Document blurred");
            assertThat(verification.getMetadata()).isEqualTo(request.getNotes());

            assertThat(driverProfile.getStatus()).isEqualTo(DriverProfileStatus.REJECTED);
            verify(driverProfileRepository).save(driverProfile);
            verify(verificationRepository).save(verification);
            verify(emailService).notifyUserRejected(user, VerificationType.DRIVER_LICENSE, "Document blurred");
            verifyNoMoreInteractions(driverProfileRepository, verificationRepository, emailService);
        }
    }

    @Test
    void should_throwValidationException_when_rejectVerificationReasonMissing() {
        // Arrange
        VerificationDecisionRequest request = VerificationDecisionRequest.builder()
                .verificationType(VerificationType.STUDENT_ID.name())
                .userId(1)
                .rejectionReason(" ")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> verificationService.rejectVerification(ADMIN_EMAIL, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Rejection reason is required");

        verifyNoInteractions(userRepository, verificationRepository, riderProfileRepository, driverProfileRepository, emailService);
    }

    @Test
    void should_updateBackgroundCheck_when_resultFailed() {
        // Arrange
        Integer driverId = 501;
        User user = createUser(driverId);
        DriverProfile driverProfile = createDriverProfile(user, DriverProfileStatus.PENDING);
        user.setDriverProfile(driverProfile);
        BackgroundCheckRequest request = BackgroundCheckRequest.builder()
                .result("failed")
                .details("Has unresolved case")
                .build();
        User admin = createUser(9999);

        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(driverProfile));
        when(verificationRepository.findByUserIdAndTypeAndStatus(driverId, VerificationType.BACKGROUND_CHECK, VerificationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        LocalDateTime now = FIXED_NOW.plusMinutes(30);
        try (MockedStatic<LocalDateTime> mockedNow = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            mockedNow.when(LocalDateTime::now).thenReturn(now);

            // Act
            MessageResponse response = verificationService.updateBackgroundCheck(ADMIN_EMAIL, driverId, request);

            // Assert
            assertThat(response.getMessage()).isEqualTo("Background check updated successfully");

            ArgumentCaptor<Verification> verificationCaptor = ArgumentCaptor.forClass(Verification.class);
            verify(verificationRepository).save(verificationCaptor.capture());
            Verification savedVerification = verificationCaptor.getValue();
            assertThat(savedVerification.getStatus()).isEqualTo(VerificationStatus.REJECTED);
            assertThat(savedVerification.getVerifiedBy()).isEqualTo(admin);
            assertThat(savedVerification.getVerifiedAt()).isEqualTo(now);
            assertThat(savedVerification.getRejectionReason()).contains("Background check failed");
            assertThat(savedVerification.getMetadata()).contains("Background check details");

            assertThat(driverProfile.getStatus()).isEqualTo(DriverProfileStatus.REJECTED);
            verify(driverProfileRepository).save(driverProfile);
            verify(userRepository).findByEmail(ADMIN_EMAIL);
            verifyNoMoreInteractions(verificationRepository, driverProfileRepository, userRepository);
        }
    }

    @Test
    void should_returnStats_when_fetchDriverVerificationStats() {
        // Arrange
        when(driverProfileRepository.count()).thenReturn(100L);
        when(verificationRepository.countByTypeAndStatus(VerificationType.DRIVER_DOCUMENTS, VerificationStatus.PENDING)).thenReturn(10L);
        when(verificationRepository.countByTypeAndStatus(VerificationType.DRIVER_LICENSE, VerificationStatus.PENDING)).thenReturn(8L);
        when(verificationRepository.countByTypeAndStatus(VerificationType.VEHICLE_REGISTRATION, VerificationStatus.PENDING)).thenReturn(5L);
        when(verificationRepository.countByTypeAndStatus(VerificationType.BACKGROUND_CHECK, VerificationStatus.PENDING)).thenReturn(2L);
        when(driverProfileRepository.countByStatus(DriverProfileStatus.ACTIVE)).thenReturn(70L);
        when(verificationRepository.countByStatus(VerificationStatus.REJECTED)).thenReturn(4L);

        // Act
        DriverStatsResponse response = verificationService.getDriverVerificationStats();

        // Assert
        assertThat(response.getTotalDrivers()).isEqualTo(100L);
        assertThat(response.getPendingVerifications()).isEqualTo(25L);
        assertThat(response.getPendingDocuments()).isEqualTo(10L);
        assertThat(response.getPendingLicenses()).isEqualTo(8L);
        assertThat(response.getPendingVehicles()).isEqualTo(5L);
        assertThat(response.getPendingBackgroundChecks()).isEqualTo(2L);
        assertThat(response.getApprovedDrivers()).isEqualTo(70L);
        assertThat(response.getRejectedVerifications()).isEqualTo(4L);
        assertThat(response.getCompletionRate()).isEqualTo(70.0);

        verify(driverProfileRepository).count();
        verify(driverProfileRepository).countByStatus(DriverProfileStatus.ACTIVE);
        verify(verificationRepository, times(4)).countByTypeAndStatus(any(VerificationType.class), eq(VerificationStatus.PENDING));
        verify(verificationRepository).countByStatus(VerificationStatus.REJECTED);
        verifyNoMoreInteractions(driverProfileRepository, verificationRepository);
    }

    @Test
    void should_returnPagedVerifications_when_requested() {
        // Arrange
        User user = createUser(33);
        Verification verification = buildVerification(600, user, VerificationType.STUDENT_ID, VerificationStatus.PENDING, FIXED_NOW.minusDays(5));
        Page<Verification> page = new PageImpl<>(List.of(verification), PageRequest.of(0, 10), 1);
        VerificationResponse mappedResponse = VerificationResponse.builder()
                .verificationId(verification.getVerificationId())
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        when(verificationRepository.findAll(pageable)).thenReturn(page);
        when(verificationMapper.mapToVerificationResponse(verification)).thenReturn(mappedResponse);

        // Act
        PageResponse<VerificationResponse> response = verificationService.getAllVerifications(pageable);

        // Assert
        assertThat(response.getData()).containsExactly(mappedResponse);
        assertThat(response.getPagination().getPage()).isEqualTo(1);
        assertThat(response.getPagination().getPageSize()).isEqualTo(10);
        assertThat(response.getPagination().getTotalPages()).isEqualTo(1);
        assertThat(response.getPagination().getTotalRecords()).isEqualTo(1L);

        verify(verificationRepository).findAll(pageable);
        verify(verificationMapper).mapToVerificationResponse(verification);
        verifyNoMoreInteractions(verificationRepository, verificationMapper);
    }

    private User createUser(Integer userId) {
        return User.builder()
                .userId(userId)
                .email("user" + userId + "@example.com")
                .phone("0900000" + userId)
                .fullName("User " + userId)
                .build();
    }

    private RiderProfile createRiderProfile(User user, RiderProfileStatus status) {
        RiderProfile profile = RiderProfile.builder()
                .riderId(user.getUserId())
                .user(user)
                .status(status)
                .build();
        user.setRiderProfile(profile);
        return profile;
    }

    private DriverProfile createDriverProfile(User user, DriverProfileStatus status) {
        DriverProfile profile = DriverProfile.builder()
                .driverId(user.getUserId())
                .user(user)
                .licenseNumber("LIC-" + user.getUserId())
                .status(status)
                .build();
        user.setDriverProfile(profile);
        return profile;
    }

    private Verification buildVerification(Integer id,
                                           User user,
                                           VerificationType type,
                                           VerificationStatus status,
                                           LocalDateTime createdAt) {
        return Verification.builder()
                .verificationId(id)
                .user(user)
                .type(type)
                .status(status)
                .createdAt(createdAt)
                .build();
    }

    private List<Verification> buildApprovedVerifications(User user) {
        return List.of(
                Verification.builder()
                        .verificationId(1001)
                        .user(user)
                        .type(VerificationType.DRIVER_LICENSE)
                        .status(VerificationStatus.APPROVED)
                        .verifiedAt(FIXED_NOW.minusDays(10))
                        .build(),
                Verification.builder()
                        .verificationId(1002)
                        .user(user)
                        .type(VerificationType.DRIVER_DOCUMENTS)
                        .status(VerificationStatus.APPROVED)
                        .verifiedAt(FIXED_NOW.minusDays(8))
                        .build(),
                Verification.builder()
                        .verificationId(1003)
                        .user(user)
                        .type(VerificationType.VEHICLE_REGISTRATION)
                        .status(VerificationStatus.APPROVED)
                        .verifiedAt(FIXED_NOW.minusDays(6))
                        .build()
        );
    }
}
