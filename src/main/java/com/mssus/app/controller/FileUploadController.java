package com.mssus.app.controller;


import com.mssus.app.service.FileUploadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "Endpoints for uploading files")
@Slf4j
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            CompletableFuture<String> uploadFuture = fileUploadService.uploadFile(file);
            String fileUrl = uploadFuture.get();
            return ResponseEntity.ok(fileUrl);
        } catch (Exception e) {
            log.error("Error uploading file", e);
            return ResponseEntity.internalServerError().body("File upload failed");
        }
    }
}
