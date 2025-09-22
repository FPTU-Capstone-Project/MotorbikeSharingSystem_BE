package com.mssus.app.service;

import com.mssus.app.dto.request.*;
import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.OtpResponse;
import com.mssus.app.dto.response.UserProfileResponse;
import com.mssus.app.dto.response.VerificationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserProfileResponse getCurrentUserProfile(String username);
    List<UserProfileResponse> getAllUserProfiles();
    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);

    MessageResponse updatePassword(String username, UpdatePasswordRequest request);

    MessageResponse updateAvatar(String username, MultipartFile file);

    MessageResponse switchProfile(String username, SwitchProfileRequest request);

    VerificationResponse submitStudentVerification(String username, MultipartFile document);

    VerificationResponse submitDriverVerification(String username, DriverVerificationRequest request);
}
