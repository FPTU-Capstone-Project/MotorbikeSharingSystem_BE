package com.mssus.app.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.mssus.app.service.FileUploadService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Value("${cloudinary.folder}")
    private String folder;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        cloudinary = new Cloudinary("cloudinary://" + apiKey + ":" + apiSecret + "@" + cloudName);
        log.info("Cloudinary initialized with cloud name: {}", cloudName);
    }


    @Override
    public CompletableFuture<String> uploadFile(MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            validateCloudinaryConfig();
            try{
                @SuppressWarnings("unchecked")
                Map<String,Object> uploadResult = (Map<String, Object>) cloudinary.uploader().upload(file.getBytes(),
                        ObjectUtils.asMap(
                                "folder", folder,
                                "resource_type", "image",
                                "use_filename", false,
                                "unique_filename", true,
                                "format", "png",
                                "quality", "auto",
                                "fetch_format", "auto"
                        ));

                String cloudinaryUrl = (String) uploadResult.get("secure_url");
                log.info("File uploaded successfully: {}", cloudinaryUrl);
                return cloudinaryUrl;
            }catch (Exception e){
                log.error("File upload failed: {}", e.getMessage());
                throw new RuntimeException("File upload failed", e);
            }
        });
    }

    private void validateCloudinaryConfig() {
        if (cloudinary == null) {
            throw new RuntimeException("Cloudinary not initialized. Please check your configuration.");
        }
    }
}
