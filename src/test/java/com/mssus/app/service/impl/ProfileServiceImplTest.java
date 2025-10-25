package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.*;
import com.mssus.app.dto.request.SwitchProfileRequest;
import com.mssus.app.dto.request.UpdatePasswordRequest;
import com.mssus.app.dto.response.*;
import com.mssus.app.entity.*;
import com.mssus.app.mapper.UserMapper;
import com.mssus.app.mapper.VerificationMapper;
import com.mssus.app.repository.*;
import com.mssus.app.security.JwtService;
import com.mssus.app.service.AuthService;
import com.mssus.app.service.FileUploadService;
import com.mssus.app.service.FPTAIService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProfileServiceImpl Tests")
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationRepository verificationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DriverProfileRepository driverProfileRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private VerificationMapper verificationMapper;

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private FPTAIService fptaiService;

    @InjectMocks
    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        // Setup is handled by @InjectMocks
    }

    // Helper methods
    private User createTestUser(Integer userId, String email, String fullName, UserType userType, 
                               Boolean emailVerified, Boolean phoneVerified) {
        return User.builder()
                .userId(userId)
                .email(email)
                .phone("0123456789")
                .fullName(fullName)
                .studentId("ST001")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .gender("Male")
                .emailVerified(emailVerified)
                .phoneVerified(phoneVerified)
                .status(UserStatus.ACTIVE)
                .userType(userType)
                .tokenVersion(1)
                .build();
    }

    private User createTestUserWithProfiles(Integer userId, String email, String fullName, UserType userType) {
        User user = createTestUser(userId, email, fullName, userType, true, true);
        
        // Add rider profile
        RiderProfile riderProfile = RiderProfile.builder()
                .user(user)
                .status(RiderProfileStatus.ACTIVE)
                .totalRides(10)
                .totalSpent(BigDecimal.valueOf(100000))
                .preferredPaymentMethod(PaymentMethod.WALLET)
                .createdAt(LocalDateTime.now())
//                .emergencyContact("113")
                .build();
        user.setRiderProfile(riderProfile);

        // Add driver profile
        DriverProfile driverProfile = DriverProfile.builder()
                .user(user)
                .status(DriverProfileStatus.ACTIVE)
                .licenseNumber("123456789")
                .ratingAvg(5.0f)
                .totalSharedRides(0)
                .totalEarned(BigDecimal.ZERO)
                .isAvailable(false)
                .maxPassengers(1)
                .maxDetourMinutes(8)
                .createdAt(LocalDateTime.now())
                .build();
        user.setDriverProfile(driverProfile);

        return user;
    }

    private UpdatePasswordRequest createUpdatePasswordRequest(String oldPassword, String newPassword) {
        return UpdatePasswordRequest.builder()
                .oldPassword(oldPassword)
                .newPassword(newPassword)
                .build();
    }

    private SwitchProfileRequest createSwitchProfileRequest(String targetProfile) {
        return SwitchProfileRequest.builder()
                .targetProfile(targetProfile)
                .build();
    }


    private MultipartFile createTestMultipartFile(String filename, String contentType, byte[] content) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getSize()).thenReturn((long) content.length);
        try {
            when(file.getBytes()).thenReturn(content);
        } catch (Exception e) {
            // This should not happen in tests
        }
        return file;
    }

    private Verification createTestVerification(User user, VerificationType type, VerificationStatus status) {
        return Verification.builder()
                .user(user)
                .type(type)
                .status(status)
                .documentUrl("https://example.com/document.jpg")
                .documentType(DocumentType.IMAGE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // Tests for getCurrentUserProfile method
    @Test
    @DisplayName("Should return admin profile when user is admin")
    void should_returnAdminProfile_when_userIsAdmin() {
        // Arrange
        String email = "admin@example.com";
        User adminUser = createTestUser(1, email, "Admin User", UserType.ADMIN, true, true);
        UserProfileResponse expectedResponse = UserProfileResponse.builder()
                .user(UserProfileResponse.UserInfo.builder()
                        .userId(1)
                        .email(email)
                        .fullName("Admin User")
                        .userType("ADMIN")
                        .build())
                .build();

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(adminUser));
        when(userMapper.toAdminProfileResponse(adminUser)).thenReturn(expectedResponse);

        // Act
        UserProfileResponse result = profileService.getCurrentUserProfile(email);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        verify(userRepository).findByEmailWithProfiles(email);
        verify(userMapper).toAdminProfileResponse(adminUser);
    }

    @Test
    @DisplayName("Should return driver profile when active profile is driver")
    void should_returnDriverProfile_when_activeProfileIsDriver() {
        // Arrange
        String email = "driver@example.com";
        User user = createTestUserWithProfiles(1, email, "Driver User", UserType.USER);
        UserProfileResponse expectedResponse = UserProfileResponse.builder()
                .user(UserProfileResponse.UserInfo.builder()
                        .userId(1)
                        .email(email)
                        .fullName("Driver User")
                        .userType("USER")
                        .build())
                .activeProfile("driver")
                .availableProfiles(List.of("driver", "rider"))
                .build();

        Map<String, Object> userContext = Map.of(
                "active_profile", "driver",
                "profiles", List.of("driver", "rider")
        );

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));
        when(authService.getUserContext(user.getUserId())).thenReturn(userContext);
        when(userMapper.toDriverProfileResponse(user)).thenReturn(expectedResponse);

        // Act
        UserProfileResponse result = profileService.getCurrentUserProfile(email);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        assertThat(result.getActiveProfile()).isEqualTo("driver");
        assertThat(result.getAvailableProfiles()).containsExactly("driver", "rider");
        verify(userRepository).findByEmailWithProfiles(email);
        verify(authService, times(2)).getUserContext(user.getUserId());
        verify(userMapper).toDriverProfileResponse(user);
    }

    @Test
    @DisplayName("Should return rider profile when active profile is rider")
    void should_returnRiderProfile_when_activeProfileIsRider() {
        // Arrange
        String email = "rider@example.com";
        User user = createTestUserWithProfiles(1, email, "Rider User", UserType.USER);
        UserProfileResponse expectedResponse = UserProfileResponse.builder()
                .user(UserProfileResponse.UserInfo.builder()
                        .userId(1)
                        .email(email)
                        .fullName("Rider User")
                        .userType("USER")
                        .build())
                .activeProfile("rider")
                .availableProfiles(List.of("rider"))
                .build();

        Map<String, Object> userContext = Map.of(
                "active_profile", "rider",
                "profiles", List.of("rider")
        );

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));
        when(authService.getUserContext(user.getUserId())).thenReturn(userContext);
        when(userMapper.toRiderProfileResponse(user)).thenReturn(expectedResponse);

        // Act
        UserProfileResponse result = profileService.getCurrentUserProfile(email);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        assertThat(result.getActiveProfile()).isEqualTo("rider");
        assertThat(result.getAvailableProfiles()).containsExactly("rider");
        verify(userRepository).findByEmailWithProfiles(email);
        verify(authService, times(2)).getUserContext(user.getUserId());
        verify(userMapper).toRiderProfileResponse(user);
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void should_throwException_when_userNotFound() {
        // Arrange
        String email = "nonexistent@example.com";
        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> profileService.getCurrentUserProfile(email))
                .isInstanceOf(BaseDomainException.class);

        verify(userRepository).findByEmailWithProfiles(email);
        verifyNoMoreInteractions(userRepository, userMapper, authService);
    }

    // Tests for updatePassword method
    @Test
    @DisplayName("Should update password successfully when old password matches")
    void should_updatePassword_when_oldPasswordMatches() {
        // Arrange
        String email = "user@example.com";
        String oldPassword = "oldPassword123";
        String newPassword = "newPassword123";
        String encodedNewPassword = "encodedNewPassword";
        
        User user = createTestUser(1, email, "Test User", UserType.USER, true, true);
        user.setPasswordHash("encodedOldPassword");
        
        UpdatePasswordRequest request = createUpdatePasswordRequest(oldPassword, newPassword);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(oldPassword, user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Act
        MessageResponse result = profileService.updatePassword(email, request);

        // Assert
        assertThat(result.getMessage()).isEqualTo("Password updated successfully");
        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).matches(oldPassword, "encodedOldPassword");
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(user);
        assertThat(user.getPasswordHash()).isEqualTo(encodedNewPassword);
    }

    @Test
    @DisplayName("Should throw exception when old password does not match")
    void should_throwException_when_oldPasswordDoesNotMatch() {
        // Arrange
        String email = "user@example.com";
        String oldPassword = "wrongPassword";
        String newPassword = "newPassword123";
        
        User user = createTestUser(1, email, "Test User", UserType.USER, true, true);
        user.setPasswordHash("encodedOldPassword");
        
        UpdatePasswordRequest request = createUpdatePasswordRequest(oldPassword, newPassword);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(oldPassword, user.getPasswordHash())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> profileService.updatePassword(email, request))
                .isInstanceOf(BaseDomainException.class);

        verify(userRepository).findByEmail(email);
        verify(passwordEncoder).matches(oldPassword, user.getPasswordHash());
        verifyNoMoreInteractions(passwordEncoder, userRepository);
    }

    @Test
    @DisplayName("Should throw exception when user not found for password update")
    void should_throwException_when_userNotFoundForPasswordUpdate() {
        // Arrange
        String email = "nonexistent@example.com";
        UpdatePasswordRequest request = createUpdatePasswordRequest("oldPassword", "newPassword");

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> profileService.updatePassword(email, request))
                .isInstanceOf(BaseDomainException.class);

        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(userRepository, passwordEncoder);
    }

    // Tests for switchProfile method
    @Test
    @DisplayName("Should switch to driver profile successfully")
    void should_switchToDriverProfile_when_validRequest() {
        // Arrange
        String email = "user@example.com";
        User user = createTestUserWithProfiles(1, email, "Test User", UserType.USER);
        SwitchProfileRequest request = createSwitchProfileRequest("driver");
        
        Map<String, Object> claims = Map.of("active_profile", "driver");
        String accessToken = "newAccessToken";

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));
        doNothing().when(authService).validateUserBeforeGrantingToken(user);
        when(authService.buildTokenClaims(user, "driver")).thenReturn(claims);
        when(jwtService.generateToken(email, claims)).thenReturn(accessToken);
        when(userRepository.save(user)).thenReturn(user);

        // Act
        SwitchProfileResponse result = profileService.switchProfile(email, request);

        // Assert
        assertThat(result.getAccessToken()).isEqualTo(accessToken);
        assertThat(result.getActiveProfile()).isEqualTo("driver");
        assertThat(user.getDriverProfile().getStatus()).isEqualTo(DriverProfileStatus.ACTIVE);
        
        verify(userRepository).findByEmailWithProfiles(email);
        verify(authService).validateUserBeforeGrantingToken(user);
        verify(authService).buildTokenClaims(user, "driver");
        verify(jwtService).generateToken(email, claims);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should switch to rider profile successfully")
    void should_switchToRiderProfile_when_validRequest() {
        // Arrange
        String email = "user@example.com";
        User user = createTestUserWithProfiles(1, email, "Test User", UserType.USER);
        SwitchProfileRequest request = createSwitchProfileRequest("rider");
        
        Map<String, Object> claims = Map.of("active_profile", "rider");
        String accessToken = "newAccessToken";

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));
        doNothing().when(authService).validateUserBeforeGrantingToken(user);
        when(authService.buildTokenClaims(user, "rider")).thenReturn(claims);
        when(jwtService.generateToken(email, claims)).thenReturn(accessToken);
        when(userRepository.save(user)).thenReturn(user);

        // Act
        SwitchProfileResponse result = profileService.switchProfile(email, request);

        // Assert
        assertThat(result.getAccessToken()).isEqualTo(accessToken);
        assertThat(result.getActiveProfile()).isEqualTo("rider");
        assertThat(user.getRiderProfile().getStatus()).isEqualTo(RiderProfileStatus.ACTIVE);
        
        verify(userRepository).findByEmailWithProfiles(email);
        verify(authService).validateUserBeforeGrantingToken(user);
        verify(authService).buildTokenClaims(user, "rider");
        verify(jwtService).generateToken(email, claims);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should throw exception when driver profile does not exist")
    void should_throwException_when_driverProfileDoesNotExist() {
        // Arrange
        String email = "user@example.com";
        User user = createTestUser(1, email, "Test User", UserType.USER, true, true);
        user.setDriverProfile(null); // No driver profile
        SwitchProfileRequest request = createSwitchProfileRequest("driver");

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> profileService.switchProfile(email, request))
                .isInstanceOf(BaseDomainException.class);

        verify(userRepository).findByEmailWithProfiles(email);
        verifyNoMoreInteractions(userRepository, authService, jwtService);
    }

    @Test
    @DisplayName("Should throw exception when driver profile is not active")
    void should_throwException_when_driverProfileNotActive() {
        // Arrange
        String email = "user@example.com";
        User user = createTestUserWithProfiles(1, email, "Test User", UserType.USER);
        user.getDriverProfile().setStatus(DriverProfileStatus.INACTIVE);
        SwitchProfileRequest request = createSwitchProfileRequest("driver");

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> profileService.switchProfile(email, request))
                .isInstanceOf(BaseDomainException.class);

        verify(userRepository).findByEmailWithProfiles(email);
        verifyNoMoreInteractions(userRepository, authService, jwtService);
    }

    // Tests for updateAvatar method
    @Test
    @DisplayName("Should update avatar successfully")
    void should_updateAvatar_when_validFileProvided() throws Exception {
        // Arrange
        String email = "user@example.com";
        String avatarUrl = "https://example.com/avatar.jpg";
        User user = createTestUser(1, email, "Test User", UserType.USER, true, true);
        
        MultipartFile avatarFile = createTestMultipartFile("avatar.jpg", "image/jpeg", "test content".getBytes());
        CompletableFuture<String> uploadFuture = CompletableFuture.completedFuture(avatarUrl);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(fileUploadService.uploadFile(avatarFile)).thenReturn(uploadFuture);
        when(userRepository.save(user)).thenReturn(user);

        // Act
        MessageResponse result = profileService.updateAvatar(email, avatarFile);

        // Assert
        assertThat(result.getMessage()).isEqualTo("Avatar uploaded successfully");
        assertThat(user.getProfilePhotoUrl()).isEqualTo(avatarUrl);
        
        verify(userRepository).findByEmail(email);
        verify(fileUploadService).uploadFile(avatarFile);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should throw exception when file upload fails")
    void should_throwException_when_fileUploadFails() throws Exception {
        // Arrange
        String email = "user@example.com";
        User user = createTestUser(1, email, "Test User", UserType.USER, true, true);
        
        MultipartFile avatarFile = createTestMultipartFile("avatar.jpg", "image/jpeg", "test content".getBytes());
        CompletableFuture<String> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Upload failed"));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(fileUploadService.uploadFile(avatarFile)).thenReturn(failedFuture);

        // Act & Assert
        assertThatThrownBy(() -> profileService.updateAvatar(email, avatarFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to upload avatar");

        verify(userRepository).findByEmail(email);
        verify(fileUploadService).uploadFile(avatarFile);
        verifyNoMoreInteractions(userRepository);
    }

    // Tests for submitStudentVerification method
    @Test
    @DisplayName("Should submit student verification successfully")
    void should_submitStudentVerification_when_validDocuments() throws Exception {
        // Arrange
        String email = "student@example.com";
        User user = createTestUser(1, email, "Student User", UserType.USER, true, true);
        
        List<MultipartFile> documents = List.of(
                createTestMultipartFile("student_id.jpg", "image/jpeg", "content1".getBytes()),
                createTestMultipartFile("student_card.jpg", "image/jpeg", "content2".getBytes())
        );
        
        List<String> documentUrls = List.of("https://example.com/doc1.jpg", "https://example.com/doc2.jpg");
        List<CompletableFuture<String>> uploadFutures = documentUrls.stream()
                .map(CompletableFuture::completedFuture)
                .toList();
        
        Verification verification = createTestVerification(user, VerificationType.STUDENT_ID, VerificationStatus.PENDING);
        VerificationResponse expectedResponse = VerificationResponse.builder()
                .verificationId(1)
                .type("STUDENT_ID")
                .status("PENDING")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(), VerificationType.STUDENT_ID, VerificationStatus.PENDING))
                .thenReturn(Optional.empty());
        when(verificationRepository.isUserVerifiedForType(user.getUserId(), VerificationType.STUDENT_ID))
                .thenReturn(false);
        when(fileUploadService.uploadFile(any(MultipartFile.class)))
                .thenReturn(uploadFutures.get(0))
                .thenReturn(uploadFutures.get(1));
        when(verificationRepository.save(any(Verification.class))).thenReturn(verification);
        when(verificationMapper.mapToVerificationResponse(verification)).thenReturn(expectedResponse);

        // Act
        VerificationResponse result = profileService.submitStudentVerification(email, documents);

        // Assert
        assertThat(result).isEqualTo(expectedResponse);
        verify(userRepository).findByEmail(email);
        verify(verificationRepository).findByUserIdAndTypeAndStatus(user.getUserId(), VerificationType.STUDENT_ID, VerificationStatus.PENDING);
        verify(verificationRepository).isUserVerifiedForType(user.getUserId(), VerificationType.STUDENT_ID);
        verify(fileUploadService, times(2)).uploadFile(any(MultipartFile.class));
        verify(verificationRepository).save(any(Verification.class));
        verify(verificationMapper).mapToVerificationResponse(verification);
    }

    @Test
    @DisplayName("Should throw exception when email not verified")
    void should_throwException_when_emailNotVerified() {
        // Arrange
        String email = "student@example.com";
        User user = createTestUser(1, email, "Student User", UserType.USER, false, true);
        List<MultipartFile> documents = List.of(createTestMultipartFile("doc.jpg", "image/jpeg", "content".getBytes()));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> profileService.submitStudentVerification(email, documents))
                .isInstanceOf(BaseDomainException.class)
                .hasMessageContaining("Email must be verified");

        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(verificationRepository, fileUploadService);
    }

    @Test
    @DisplayName("Should throw exception when phone not verified")
    void should_throwException_when_phoneNotVerified() {
        // Arrange
        String email = "student@example.com";
        User user = createTestUser(1, email, "Student User", UserType.USER, true, false);
        List<MultipartFile> documents = List.of(createTestMultipartFile("doc.jpg", "image/jpeg", "content".getBytes()));

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> profileService.submitStudentVerification(email, documents))
                .isInstanceOf(BaseDomainException.class)
                .hasMessageContaining("Phone must be verified");

        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(verificationRepository, fileUploadService);
    }

    @Test
    @DisplayName("Should throw exception when documents are empty")
    void should_throwException_when_documentsEmpty() {
        // Arrange
        String email = "student@example.com";
        User user = createTestUser(1, email, "Student User", UserType.USER, true, true);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> profileService.submitStudentVerification(email, List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("At least one documents to upload");

        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(verificationRepository, fileUploadService);
    }

    @Test
    @DisplayName("Should throw exception when student verification already exists")
    void should_throwException_when_studentVerificationAlreadyExists() {
        // Arrange
        String email = "student@example.com";
        User user = createTestUser(1, email, "Student User", UserType.USER, true, true);
        List<MultipartFile> documents = List.of(createTestMultipartFile("doc.jpg", "image/jpeg", "content".getBytes()));
        
        Verification existingVerification = createTestVerification(user, VerificationType.STUDENT_ID, VerificationStatus.PENDING);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(verificationRepository.findByUserIdAndTypeAndStatus(user.getUserId(), VerificationType.STUDENT_ID, VerificationStatus.PENDING))
                .thenReturn(Optional.of(existingVerification));

        // Act & Assert
        assertThatThrownBy(() -> profileService.submitStudentVerification(email, documents))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Student verification already exists");

        verify(userRepository).findByEmail(email);
        verify(verificationRepository).findByUserIdAndTypeAndStatus(user.getUserId(), VerificationType.STUDENT_ID, VerificationStatus.PENDING);
        verifyNoMoreInteractions(fileUploadService);
    }

    // Tests for getAllUsers method
    @Test
    @DisplayName("Should return paginated users successfully")
    void should_returnPaginatedUsers_when_validPageable() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = List.of(
                createTestUser(1, "user1@example.com", "User 1", UserType.USER, true, true),
                createTestUser(2, "user2@example.com", "User 2", UserType.USER, true, true)
        );
        Page<User> userPage = new PageImpl<>(users, pageable, 2);
        
        List<UserResponse> userResponses = List.of(
                UserResponse.builder().userId(1).email("user1@example.com").fullName("User 1").build(),
                UserResponse.builder().userId(2).email("user2@example.com").fullName("User 2").build()
        );

        when(userRepository.findAll(pageable)).thenReturn(userPage);
        when(userMapper.toUserResponse(users.get(0))).thenReturn(userResponses.get(0));
        when(userMapper.toUserResponse(users.get(1))).thenReturn(userResponses.get(1));

        // Act
        PageResponse<UserResponse> result = profileService.getAllUsers(pageable);

        // Assert
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData()).containsExactlyElementsOf(userResponses);
        assertThat(result.getPagination().getPage()).isEqualTo(1);
        assertThat(result.getPagination().getPageSize()).isEqualTo(10);
        assertThat(result.getPagination().getTotalPages()).isEqualTo(1);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(2);
        
        verify(userRepository).findAll(pageable);
        verify(userMapper, times(2)).toUserResponse(any(User.class));
    }

    // Tests for setDriverStatus method
    @Test
    @DisplayName("Should set driver status to active successfully")
    void should_setDriverStatusToActive_when_validUser() {
        // Arrange
        String email = "driver@example.com";
        User user = createTestUserWithProfiles(1, email, "Driver User", UserType.USER);
        user.getDriverProfile().setStatus(DriverProfileStatus.INACTIVE);

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenReturn(user.getDriverProfile());

        // Act
        profileService.setDriverStatus(email, true);

        // Assert
        assertThat(user.getDriverProfile().getStatus()).isEqualTo(DriverProfileStatus.ACTIVE);
        verify(userRepository).findByEmailWithProfiles(email);
        verify(driverProfileRepository).save(user.getDriverProfile());
    }

    @Test
    @DisplayName("Should set driver status to inactive successfully")
    void should_setDriverStatusToInactive_when_validUser() {
        // Arrange
        String email = "driver@example.com";
        User user = createTestUserWithProfiles(1, email, "Driver User", UserType.USER);
        user.getDriverProfile().setStatus(DriverProfileStatus.ACTIVE);

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));
        when(driverProfileRepository.save(any(DriverProfile.class))).thenReturn(user.getDriverProfile());

        // Act
        profileService.setDriverStatus(email, false);

        // Assert
        assertThat(user.getDriverProfile().getStatus()).isEqualTo(DriverProfileStatus.INACTIVE);
        verify(userRepository).findByEmailWithProfiles(email);
        verify(driverProfileRepository).save(user.getDriverProfile());
    }

    @Test
    @DisplayName("Should throw exception when user has no driver profile")
    void should_throwException_when_userHasNoDriverProfile() {
        // Arrange
        String email = "user@example.com";
        User user = createTestUser(1, email, "User", UserType.USER, true, true);
        user.setDriverProfile(null);

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThatThrownBy(() -> profileService.setDriverStatus(email, true))
                .isInstanceOf(BaseDomainException.class)
                .hasMessageContaining("User does not have a driver profile");

        verify(userRepository).findByEmailWithProfiles(email);
        verifyNoMoreInteractions(driverProfileRepository);
    }

    @Test
    @DisplayName("Should not save when status is already the same")
    void should_notSave_when_statusAlreadySame() {
        // Arrange
        String email = "driver@example.com";
        User user = createTestUserWithProfiles(1, email, "Driver User", UserType.USER);
        user.getDriverProfile().setStatus(DriverProfileStatus.ACTIVE);

        when(userRepository.findByEmailWithProfiles(email)).thenReturn(Optional.of(user));

        // Act
        profileService.setDriverStatus(email, true);

        // Assert
        assertThat(user.getDriverProfile().getStatus()).isEqualTo(DriverProfileStatus.ACTIVE);
        verify(userRepository).findByEmailWithProfiles(email);
        verifyNoMoreInteractions(driverProfileRepository);
    }

}
