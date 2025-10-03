package com.mssus.app.service.impl;


import com.mssus.app.dto.response.MessageResponse;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.StudentVerificationResponse;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Verification;
import com.mssus.app.mapper.VerificationMapper;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.VerificationRepository;
import com.mssus.app.service.FileUploadService;
import com.mssus.app.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final VerificationRepository verificationRepository;
    private final VerificationMapper verificationMapper;
    private final FileUploadService fileUploadService;

    @Override
    public PageResponse<StudentVerificationResponse> getUsersVerificationHistory(Authentication authentication, Pageable pageable) {
        Page<Verification> verifications = verificationRepository.findByUser(userRepository.findByEmail(authentication.getName()).get().getUserId(), pageable);
        List<StudentVerificationResponse> content = verifications.getContent().stream()
                .map(verificationMapper::mapToStudentVerificationResponse)
                .toList();
        return buildPageResponse(verifications, content);
    }

    @Override
    public MessageResponse uploadAvatar(Authentication authentication, MultipartFile avatarFile) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow(() -> new RuntimeException("User not found"));
        try{
            String profilePhotoUrl = fileUploadService.uploadFile(avatarFile).get();
            user.setProfilePhotoUrl(profilePhotoUrl);
            userRepository.save(user);
            return MessageResponse.builder()
                    .message("Avatar uploaded successfully")
                    .build();
        }catch (Exception e){
            throw new RuntimeException("Failed to upload avatar: " + e.getMessage());
        }
    }

    @Override
    public MessageResponse uploadDocumentProof(Authentication authentication, MultipartFile cardUrl,String type) {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow(() -> new RuntimeException("User not found"));
        try{
//            String studentCardUrl = fileUploadService.uploadFile(idCardUrl).get();
//            user.setIdCardUrl(studentCardUrl);
//            userRepository.save(user);
            return MessageResponse.builder()
                    .message("Student card uploaded successfully")
                    .build();
        }catch (Exception e){
            throw new RuntimeException("Failed to upload student card: " + e.getMessage());
        }
    }
    


    private <T> PageResponse<T> buildPageResponse (Page<?> page, List<T> content){
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
