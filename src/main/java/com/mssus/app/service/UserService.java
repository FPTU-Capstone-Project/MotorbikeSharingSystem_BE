package com.mssus.app.service;

import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.StudentVerificationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    PageResponse<StudentVerificationResponse> getUsersVerificationHistory(Authentication authentication,Pageable pageable);
    MessageResponse uploadAvatar(Authentication authentication, MultipartFile avatarFile);
    MessageResponse uploadDocumentProof(Authentication authentication, MultipartFile cardUrl,String type);
}
