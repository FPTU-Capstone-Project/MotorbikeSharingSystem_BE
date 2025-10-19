package com.mssus.app.service;

import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProfileService {
    UserProfileResponse getCurrentUserProfile(String username);

    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);

    MessageResponse updatePassword(String username, UpdatePasswordRequest request);

    MessageResponse updateAvatar(String username, MultipartFile avatarFile);

    SwitchProfileResponse switchProfile(String username, SwitchProfileRequest request);

    VerificationResponse submitStudentVerification(String username, List<MultipartFile> documents);

    VerificationResponse submitDriverLicense(String username, List<MultipartFile> documents);

    VerificationResponse submitDriverDocuments(String username, List<MultipartFile> documents);

    VerificationResponse submitVehicleRegistration(String username, List<MultipartFile> documents);

    PageResponse<UserResponse> getAllUsers(Pageable pageable);

    void setDriverStatus(String username, boolean isActive);
}
