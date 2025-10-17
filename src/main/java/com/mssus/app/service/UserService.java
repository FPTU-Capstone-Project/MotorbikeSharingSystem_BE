package com.mssus.app.service;

import com.mssus.app.dto.request.CreateUserRequest;
import com.mssus.app.dto.request.UpdateUserRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.UserResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {

    /**
     * Create a new user (Admin only)
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Get user by ID
     */
    UserResponse getUserById(Integer userId);

    /**
     * Get user by email
     */
    UserResponse getUserByEmail(String email);

    /**
     * Update user (Admin only)
     */
    UserResponse updateUser(Integer userId, UpdateUserRequest request);

    /**
     * Delete user (Admin only)
     */
    void deleteUser(Integer userId);

    /**
     * Get all users with pagination (Admin only)
     */
    PageResponse<UserResponse> getAllUsers(Pageable pageable);

    /**
     * Get users by status with pagination (Admin only)
     */
    PageResponse<UserResponse> getUsersByStatus(String status, Pageable pageable);

    /**
     * Get users by type with pagination (Admin only)
     */
    PageResponse<UserResponse> getUsersByType(String userType, Pageable pageable);

    /**
     * Suspend user (Admin only)
     */
    UserResponse suspendUser(Integer userId);

    /**
     * Activate user (Admin only)
     */
    UserResponse activateUser(Integer userId);
}
