package com.mssus.app.service;

import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

public interface FPTAIService {
    String analyzeDocument(MultipartFile file, VerificationType type);

    boolean verifyDriverLicense(User user, List<MultipartFile> documents);
}
