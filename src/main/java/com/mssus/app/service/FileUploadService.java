package com.mssus.app.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

public interface FileUploadService {

    CompletableFuture<String> uploadFile(MultipartFile file);

}
