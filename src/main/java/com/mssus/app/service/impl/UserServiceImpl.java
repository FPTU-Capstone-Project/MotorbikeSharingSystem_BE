package com.mssus.app.service.impl;

import com.mssus.app.common.enums.DriverProfileStatus;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.enums.UserType;
import com.mssus.app.dto.request.CreateUserRequest;
import com.mssus.app.dto.request.UpdateUserRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.UserResponse;
import com.mssus.app.entity.User;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.RiderProfileRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.UserService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email đã tồn tại: " + request.getEmail());
        }

        // Check if phone already exists
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại: " + request.getPhone());
        }

        // Check if student ID already exists (if provided)
        if (request.getStudentId() != null && !request.getStudentId().trim().isEmpty()) {
            if (userRepository.existsByStudentId(request.getStudentId())) {
                throw new IllegalArgumentException("Mã sinh viên đã tồn tại: " + request.getStudentId());
            }
        }

        // Build user entity
        User.UserBuilder userBuilder = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .userType(UserType.valueOf(request.getUserType()))
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .phoneVerified(false);

        // Set optional fields
        if (request.getStudentId() != null && !request.getStudentId().trim().isEmpty()) {
            userBuilder.studentId(request.getStudentId());
        }

        if (request.getDateOfBirth() != null && !request.getDateOfBirth().trim().isEmpty()) {
            userBuilder.dateOfBirth(LocalDate.parse(request.getDateOfBirth(), DateTimeFormatter.ISO_LOCAL_DATE));
        }

        if (request.getGender() != null && !request.getGender().trim().isEmpty()) {
            userBuilder.gender(request.getGender());
        }

        User user = userBuilder.build();
        user = userRepository.save(user);

        // Create wallet for the user
        walletService.createWalletForUser(user.getUserId());

        log.info("Created user with ID: {} and email: {}", user.getUserId(), user.getEmail());
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserById(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với email: " + email));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Integer userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));

        // Update fields if provided
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email đã tồn tại: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new IllegalArgumentException("Số điện thoại đã tồn tại: " + request.getPhone());
            }
            user.setPhone(request.getPhone());
        }

        if (request.getUserType() != null) {
            user.setUserType(UserType.valueOf(request.getUserType()));
        }

        if (request.getStudentId() != null) {
            if (!request.getStudentId().trim().isEmpty() && userRepository.existsByStudentId(request.getStudentId())) {
                throw new IllegalArgumentException("Mã sinh viên đã tồn tại: " + request.getStudentId());
            }
            user.setStudentId(request.getStudentId());
        }

        if (request.getDateOfBirth() != null) {
            if (request.getDateOfBirth().trim().isEmpty()) {
                user.setDateOfBirth(null);
            } else {
                user.setDateOfBirth(LocalDate.parse(request.getDateOfBirth(), DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }

        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }

        if (request.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(request.getStatus()));
        }

        if (request.getEmailVerified() != null) {
            user.setEmailVerified(request.getEmailVerified());
        }

        if (request.getPhoneVerified() != null) {
            user.setPhoneVerified(request.getPhoneVerified());
        }

        user = userRepository.save(user);
        log.info("Updated user with ID: {}", userId);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));

        // Soft delete by setting status to SUSPENDED
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        // Update rider profile status to SUSPENDED if exists
        riderProfileRepository.findByUserUserId(userId)
                .ifPresent(riderProfile -> {
                    riderProfile.setStatus(RiderProfileStatus.SUSPENDED);
                    riderProfileRepository.save(riderProfile);
                    log.info("Suspended rider profile for user ID: {}", userId);
                });

        // Update driver profile status to SUSPENDED if exists
        driverProfileRepository.findByUserUserId(userId)
                .ifPresent(driverProfile -> {
                    driverProfile.setStatus(DriverProfileStatus.SUSPENDED);
                    driverProfileRepository.save(driverProfile);
                    log.info("Suspended driver profile for user ID: {}", userId);
                });

        log.info("Soft deleted user with ID: {} and suspended associated profiles", userId);
    }

    @Override
    public PageResponse<UserResponse> getAllUsers(Pageable pageable) {
        Page<User> usersPage = userRepository.findAll(pageable);
        List<UserResponse> users = usersPage.getContent().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
        return buildPageResponse(usersPage, users);
    }

    @Override
    public PageResponse<UserResponse> getUsersByStatus(String status, Pageable pageable) {
        UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
        Page<User> usersPage = userRepository.findByStatus(userStatus, pageable);
        List<UserResponse> users = usersPage.getContent().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
        return buildPageResponse(usersPage, users);
    }

    @Override
    public PageResponse<UserResponse> getUsersByType(String userType, Pageable pageable) {
        UserType type = UserType.valueOf(userType.toUpperCase());
        Page<User> usersPage = userRepository.findByUserType(type, pageable);
        List<UserResponse> users = usersPage.getContent().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
        return buildPageResponse(usersPage, users);
    }

    @Override
    @Transactional
    public UserResponse suspendUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));

        user.setStatus(UserStatus.SUSPENDED);
        user = userRepository.save(user);

        log.info("Suspended user with ID: {}", userId);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse activateUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + userId));

        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        log.info("Activated user with ID: {}", userId);
        return mapToUserResponse(user);
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .studentId(user.getStudentId())
                .userType(user.getUserType() != null ? user.getUserType().name() : null)
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .emailVerified(user.getEmailVerified())
                .phoneVerified(user.getPhoneVerified())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private PageResponse<UserResponse> buildPageResponse(Page<User> page, List<UserResponse> content) {
        PageResponse.PaginationInfo pagination = PageResponse.PaginationInfo.builder()
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalRecords(page.getTotalElements())
                .build();
        
        return PageResponse.<UserResponse>builder()
                .data(content)
                .pagination(pagination)
                .build();
    }
}
