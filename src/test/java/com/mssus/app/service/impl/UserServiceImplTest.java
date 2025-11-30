package com.mssus.app.service.impl;

import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.dto.request.CreateUserRequest;
import com.mssus.app.dto.request.UpdateUserRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.UserResponse;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("UserServiceImpl Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private CreateUserRequest testCreateRequest;
    private UpdateUserRequest testUpdateRequest;

    @BeforeEach
    void setUp() {
        setupTestData();
        setupMockBehavior();
    }

    private void setupTestData() {
        testUser = createTestUser();
        testCreateRequest = createTestCreateUserRequest();
        testUpdateRequest = createTestUpdateUserRequest();
    }

    private void setupMockBehavior() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRepository.findById(anyInt())).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(walletService.createWalletForUser(anyInt())).thenReturn(mock(Wallet.class));
    }

    // ========== CREATE USER TESTS ==========

    @Test
    @DisplayName("should_createUser_when_validRequest")
    void should_createUser_when_validRequest() {
        // Arrange
        when(userRepository.existsByEmail(testCreateRequest.getEmail())).thenReturn(false);

        // Act
        UserResponse result = userService.createUser(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(testCreateRequest.getEmail());
        assertThat(result.getUserType()).isEqualTo(testCreateRequest.getUserType());
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE.name());

        verify(userRepository).existsByEmail(testCreateRequest.getEmail());
        verify(passwordEncoder).encode(anyString());
        verify(userRepository).save(any(User.class));
        verify(walletService).createWalletForUser(testUser.getUserId());
    }

    @Test
    @DisplayName("should_throwException_when_emailAlreadyExists")
    void should_throwException_when_emailAlreadyExists() {
        // Arrange
        when(userRepository.existsByEmail(testCreateRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(testCreateRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email đã tồn tại: " + testCreateRequest.getEmail());

        verify(userRepository).existsByEmail(testCreateRequest.getEmail());
    }

    /* Commented out - phone and studentId validations removed from CreateUserRequest
    @Test
    @DisplayName("should_throwException_when_phoneAlreadyExists")
    void should_throwException_when_phoneAlreadyExists() {
        // Arrange
        when(userRepository.existsByPhone(testCreateRequest.getPhone())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(testCreateRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Phone already exists: " + testCreateRequest.getPhone());

        verify(userRepository).existsByEmail(testCreateRequest.getEmail());
        verify(userRepository).existsByPhone(testCreateRequest.getPhone());
        verifyNoMoreInteractions(userRepository, passwordEncoder, walletService);
    }

    @Test
    @DisplayName("should_throwException_when_studentIdAlreadyExists")
    void should_throwException_when_studentIdAlreadyExists() {
        // Arrange
        when(userRepository.existsByStudentId(testCreateRequest.getStudentId())).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(testCreateRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Student ID already exists: " + testCreateRequest.getStudentId());

        verify(userRepository).existsByEmail(testCreateRequest.getEmail());
        verify(userRepository).existsByPhone(testCreateRequest.getPhone());
        verify(userRepository).existsByStudentId(testCreateRequest.getStudentId());
        verifyNoMoreInteractions(userRepository, passwordEncoder, walletService);
    }

    @Test
    @DisplayName("should_createUser_when_studentIdIsNull")
    void should_createUser_when_studentIdIsNull() {
        // Arrange
        testCreateRequest.setStudentId(null);
        when(userRepository.existsByEmail(testCreateRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone(testCreateRequest.getPhone())).thenReturn(false);
        
        // Create a user without student ID for the mock return
        User userWithoutStudentId = createTestUser();
        userWithoutStudentId.setStudentId(null);
        when(userRepository.save(any(User.class))).thenReturn(userWithoutStudentId);

        // Act
        UserResponse result = userService.createUser(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isNull();

        verify(userRepository).existsByEmail(testCreateRequest.getEmail());
        verify(userRepository).existsByPhone(testCreateRequest.getPhone());
        verify(userRepository, never()).existsByStudentId(anyString());
        verify(userRepository).save(any(User.class));
        verify(walletService).createWalletForUser(userWithoutStudentId.getUserId());
    }

    @Test
    @DisplayName("should_createUser_when_studentIdIsEmpty")
    void should_createUser_when_studentIdIsEmpty() {
        // Arrange
        testCreateRequest.setStudentId("");
        when(userRepository.existsByEmail(testCreateRequest.getEmail())).thenReturn(false);
        when(userRepository.existsByPhone(testCreateRequest.getPhone())).thenReturn(false);
        
        // Create a user without student ID for the mock return
        User userWithoutStudentId = createTestUser();
        userWithoutStudentId.setStudentId(null);
        when(userRepository.save(any(User.class))).thenReturn(userWithoutStudentId);

        // Act
        UserResponse result = userService.createUser(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isNull();

        verify(userRepository).existsByEmail(testCreateRequest.getEmail());
        verify(userRepository).existsByPhone(testCreateRequest.getPhone());
        verify(userRepository, never()).existsByStudentId(anyString());
        verify(userRepository).save(any(User.class));
        verify(walletService).createWalletForUser(userWithoutStudentId.getUserId());
    }
    */

    // ========== GET USER BY ID TESTS ==========

    @Test
    @DisplayName("should_getUserById_when_userExists")
    void should_getUserById_when_userExists() {
        // Arrange
        Integer userId = 1;

        // Act
        UserResponse result = userService.getUserById(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
        assertThat(result.getPhone()).isEqualTo(testUser.getPhone());
        assertThat(result.getFullName()).isEqualTo(testUser.getFullName());

        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_throwException_when_userNotFoundById")
    void should_throwException_when_userNotFoundById() {
        // Arrange
        Integer userId = 999;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found with ID: " + userId);

        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(userRepository);
    }

    // ========== GET USER BY EMAIL TESTS ==========

    @Test
    @DisplayName("should_getUserByEmail_when_userExists")
    void should_getUserByEmail_when_userExists() {
        // Arrange
        String email = "test@example.com";

        // Act
        UserResponse result = userService.getUserByEmail(email);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getPhone()).isEqualTo(testUser.getPhone());
        assertThat(result.getFullName()).isEqualTo(testUser.getFullName());

        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_throwException_when_userNotFoundByEmail")
    void should_throwException_when_userNotFoundByEmail() {
        // Arrange
        String email = "notfound@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserByEmail(email))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found with email: " + email);

        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(userRepository);
    }

    // ========== UPDATE USER TESTS ==========

    @Test
    @DisplayName("should_updateUser_when_validRequest")
    void should_updateUser_when_validRequest() {
        // Arrange
        Integer userId = 1;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        UserResponse result = userService.updateUser(userId, testUpdateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getFullName()).isEqualTo(testUpdateRequest.getFullName());
        assertThat(result.getEmail()).isEqualTo(testUpdateRequest.getEmail());
        assertThat(result.getPhone()).isEqualTo(testUpdateRequest.getPhone());

        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
        // Don't use verifyNoMoreInteractions as the service may call other repository methods
    }

    @Test
    @DisplayName("should_throwException_when_userNotFoundForUpdate")
    void should_throwException_when_userNotFoundForUpdate() {
        // Arrange
        Integer userId = 999;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUser(userId, testUpdateRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found with ID: " + userId);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_updateUser_when_partialRequest")
    void should_updateUser_when_partialRequest() {
        // Arrange
        Integer userId = 1;
        UpdateUserRequest partialRequest = UpdateUserRequest.builder()
            .fullName("Updated Name")
            .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        UserResponse result = userService.updateUser(userId, partialRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo("Updated Name");
        assertThat(result.getEmail()).isEqualTo(testUser.getEmail()); // Should remain unchanged

        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
    }

    // ========== DELETE USER TESTS ==========

    @Test
    @DisplayName("should_deleteUser_when_userExists")
    void should_deleteUser_when_userExists() {
        // Arrange
        Integer userId = 1;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        userService.deleteUser(userId);

        // Assert
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_throwException_when_userNotFoundForDelete")
    void should_throwException_when_userNotFoundForDelete() {
        // Arrange
        Integer userId = 999;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found with ID: " + userId);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }

    // ========== GET ALL USERS TESTS ==========

    @Test
    @DisplayName("should_getAllUsers_when_usersExist")
    void should_getAllUsers_when_usersExist() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = List.of(testUser, createTestUser(2, "user2@example.com"));
        Page<User> userPage = new PageImpl<>(users, pageable, 2);
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        // Act
        PageResponse<UserResponse> result = userService.getAllUsers(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(2);
        assertThat(result.getPagination().getPage()).isEqualTo(0);
        assertThat(result.getPagination().getPageSize()).isEqualTo(10);

        verify(userRepository).findAll(pageable);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_getAllUsers_when_noUsersExist")
    void should_getAllUsers_when_noUsersExist() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(userRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act
        PageResponse<UserResponse> result = userService.getAllUsers(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(0);

        verify(userRepository).findAll(pageable);
        verifyNoMoreInteractions(userRepository);
    }

    // ========== GET USERS BY STATUS TESTS ==========

    @Test
    @DisplayName("should_getUsersByStatus_when_usersExist")
    void should_getUsersByStatus_when_usersExist() {
        // Arrange
        String status = "ACTIVE";
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = List.of(testUser);
        Page<User> userPage = new PageImpl<>(users, pageable, 1);
        when(userRepository.findByStatus(UserStatus.ACTIVE, pageable)).thenReturn(userPage);

        // Act
        PageResponse<UserResponse> result = userService.getUsersByStatus(status, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(1);

        verify(userRepository).findByStatus(UserStatus.ACTIVE, pageable);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_getUsersByStatus_when_noUsersExist")
    void should_getUsersByStatus_when_noUsersExist() {
        // Arrange
        String status = "SUSPENDED";
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(userRepository.findByStatus(UserStatus.SUSPENDED, pageable)).thenReturn(emptyPage);

        // Act
        PageResponse<UserResponse> result = userService.getUsersByStatus(status, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(0);

        verify(userRepository).findByStatus(UserStatus.SUSPENDED, pageable);
        verifyNoMoreInteractions(userRepository);
    }

    // ========== GET USERS BY TYPE TESTS ==========

    @Test
    @DisplayName("should_getUsersByType_when_usersExist")
    void should_getUsersByType_when_usersExist() {
        // Arrange
        String userType = "USER";
        Pageable pageable = PageRequest.of(0, 10);
        List<User> users = List.of(testUser);
        Page<User> userPage = new PageImpl<>(users, pageable, 1);
        when(userRepository.findByUserType(UserType.USER, pageable)).thenReturn(userPage);

        // Act
        PageResponse<UserResponse> result = userService.getUsersByType(userType, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(1);

        verify(userRepository).findByUserType(UserType.USER, pageable);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_getUsersByType_when_noUsersExist")
    void should_getUsersByType_when_noUsersExist() {
        // Arrange
        String userType = "ADMIN";
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(userRepository.findByUserType(UserType.ADMIN, pageable)).thenReturn(emptyPage);

        // Act
        PageResponse<UserResponse> result = userService.getUsersByType(userType, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotalRecords()).isEqualTo(0);

        verify(userRepository).findByUserType(UserType.ADMIN, pageable);
        verifyNoMoreInteractions(userRepository);
    }

    // ========== SUSPEND USER TESTS ==========

    @Test
    @DisplayName("should_suspendUser_when_userExists")
    void should_suspendUser_when_userExists() {
        // Arrange
        Integer userId = 1;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        UserResponse result = userService.suspendUser(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.SUSPENDED);

        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_throwException_when_userNotFoundForSuspend")
    void should_throwException_when_userNotFoundForSuspend() {
        // Arrange
        Integer userId = 999;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.suspendUser(userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found with ID: " + userId);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }

    // ========== ACTIVATE USER TESTS ==========

    @Test
    @DisplayName("should_activateUser_when_userExists")
    void should_activateUser_when_userExists() {
        // Arrange
        Integer userId = 1;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        UserResponse result = userService.activateUser(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(testUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        verify(userRepository).findById(userId);
        verify(userRepository).save(testUser);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("should_throwException_when_userNotFoundForActivate")
    void should_throwException_when_userNotFoundForActivate() {
        // Arrange
        Integer userId = 999;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.activateUser(userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User not found with ID: " + userId);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any(User.class));
        verifyNoMoreInteractions(userRepository);
    }

    // ========== PARAMETERIZED TESTS ==========

    @ParameterizedTest
    @MethodSource("userTypeProvider")
    @DisplayName("should_createUser_when_differentUserTypes")
    void should_createUser_when_differentUserTypes(String userType) {
        // Arrange
        testCreateRequest.setUserType(userType);
        when(userRepository.existsByEmail(testCreateRequest.getEmail())).thenReturn(false);
        
        // Create a user with the specific user type for the mock return
        User userWithType = createTestUser();
        userWithType.setUserType(UserType.valueOf(userType));
        when(userRepository.save(any(User.class))).thenReturn(userWithType);

        // Act
        UserResponse result = userService.createUser(testCreateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUserType()).isEqualTo(userType);

        verify(userRepository).save(any(User.class));
        verify(walletService).createWalletForUser(userWithType.getUserId());
    }

    @ParameterizedTest
    @MethodSource("userStatusProvider")
    @DisplayName("should_getUsersByStatus_when_differentStatuses")
    void should_getUsersByStatus_when_differentStatuses(String status) {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(userRepository.findByStatus(UserStatus.valueOf(status.toUpperCase()), pageable))
            .thenReturn(emptyPage);

        // Act
        PageResponse<UserResponse> result = userService.getUsersByStatus(status, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getData()).isEmpty();

        verify(userRepository).findByStatus(UserStatus.valueOf(status.toUpperCase()), pageable);
    }

    // ========== HELPER METHODS ==========

    private User createTestUser() {
        return createTestUser(1, "test@example.com");
    }

    private User createTestUser(Integer userId, String email) {
        return User.builder()
            .userId(userId)
            .email(email)
            .phone("0901234567")
            .passwordHash("encoded_password")
            .fullName("Test User")
            .studentId("SE123456")
            .dateOfBirth(LocalDate.of(2000, 1, 1))
            .gender("MALE")
            .userType(UserType.USER)
            .status(UserStatus.ACTIVE)
            .emailVerified(false)
            .phoneVerified(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private CreateUserRequest createTestCreateUserRequest() {
        return CreateUserRequest.builder()
            .email("test@example.com")
            .userType("USER")
            .build();
    }

    private UpdateUserRequest createTestUpdateUserRequest() {
        return UpdateUserRequest.builder()
            .fullName("Updated User")
            .email("updated@example.com")
            .phone("0907654321")
            .userType("USER")
            .studentId("SE654321")
            .dateOfBirth("2000-01-01")
            .gender("FEMALE")
            .status("ACTIVE")
            .emailVerified(true)
            .phoneVerified(true)
            .build();
    }

    private static Stream<String> userTypeProvider() {
        return Stream.of("USER", "ADMIN");
    }

    private static Stream<String> userStatusProvider() {
        return Stream.of("ACTIVE", "SUSPENDED", "PENDING", "EMAIL_VERIFYING", "REJECTED", "DELETED");
    }
}
