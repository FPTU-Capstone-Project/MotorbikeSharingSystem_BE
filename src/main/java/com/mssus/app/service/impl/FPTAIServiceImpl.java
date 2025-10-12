package com.mssus.app.service.impl;

import com.mssus.app.common.enums.VerificationType;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.entity.User;
import com.mssus.app.service.FPTAIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class FPTAIServiceImpl implements FPTAIService {

    @Value("${fpt.ai.api-key}")
    private String apiKey;
    @Override
    public String analyzeDocument(MultipartFile file, VerificationType type) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String OCR_URL = switch (type) {
                case DRIVER_LICENSE -> "https://api.fpt.ai/vision/dlr/vnm";
                case VEHICLE_REGISTRATION -> "https://api.fpt.ai/vision/idr/vnm";
                default -> throw new IllegalArgumentException("Unsupported type");
            };

            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);


            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };


            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(OCR_URL, requestEntity, String.class);

            return response.getBody();

        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage());
        }
    }


    @Override
    public boolean verifyDriverLicense(User user, MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw ValidationException.of("Driver license image is required for verification");
        }

        log.info("👤 Bắt đầu xác thực GPLX cho người dùng: {}", user.getEmail());

        String ocrJson = analyzeDocument(document, VerificationType.DRIVER_LICENSE);
        JSONObject json = new JSONObject(ocrJson);
        log.debug("📄 FPT.AI OCR raw JSON:\n{}", json.toString(2));

        // Extract structured fields từ JSON
        String name = "";
        String id = "";
        String dob = "";
        String doe = "";
        String type = "";

        if (json.has("data") && json.get("data") instanceof JSONArray) {
            JSONArray arr = json.getJSONArray("data");
            if (!arr.isEmpty()) {
                JSONObject data = arr.getJSONObject(0);

                name = data.optString("name", "");
                id = data.optString("id", "");
                dob = data.optString("dob", "");
                doe = data.optString("doe", "");
                type = data.optString("type", "");

                log.info("""
                === FPT.AI Driver License OCR (Front Side) ===
                🪪 Name: {}
                🔢 ID: {}
                🎂 DOB: {}
                📅 DOE (expiry): {}
                🚗 Type: {}
                """,
                        name, id, dob, doe, type
                );
            }
        }

        // Nếu structured data thiếu, fallback OCR text
        String text = extractOcrText(json).trim();
        log.info("📜 OCR Raw Text (Driver License):\n{}", text);

        if (name.isEmpty()) {
            name = extractValue(text, "(?i)(Họ và tên|Full name|Họ tên)[:\\s]+([A-ZÀ-Ỹ\\s]+)");
        }
        if (id.isEmpty()) {
            id = extractValue(text, "(?i)(Số|No)[:\\s]*([A-Z0-9]+)");
        }
        if (dob.isEmpty()) {
            dob = extractValue(text, "(?i)\\b(\\d{2}/\\d{2}/\\d{4})\\b");
        }
        if (doe.isEmpty()) {
            doe = extractValue(text, "(?i)(Có giá trị đến|Ngày hết hạn)[:\\s]*(\\d{2}/\\d{2}/\\d{4}|KHÔNG THỜI HẠN)");
        }

        // === VALIDATION ===
        if (name.isEmpty() || !user.getFullName().equalsIgnoreCase(name)) {
            log.warn("❌ Tên trên GPLX không khớp: expected={}, found={}", user.getFullName(), name);
            return false;
        }

        if (id.isEmpty()) {
            log.warn("❌ Không tìm thấy số GPLX");
            return false;
        }

        if (doe.equalsIgnoreCase("KHÔNG THỜI HẠN")) {
            log.info("✅ GPLX hợp lệ (Không thời hạn) cho người dùng {}", user.getEmail());
            return true;
        }

        if (doe.isEmpty()) {
            log.warn("❌ Không tìm thấy ngày hết hạn GPLX");
            return false;
        }

        try {
            LocalDate expiry = LocalDate.parse(doe, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (expiry.isBefore(LocalDate.now())) {
                log.warn("❌ GPLX đã hết hạn: {}", doe);
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ Không thể parse ngày hết hạn: {}", doe);
        }

        log.info("✅ GPLX mặt trước hợp lệ cho người dùng {}", user.getEmail());
        return true;
    }



    private String extractOcrText(JSONObject json) {
        if (!json.has("data")) return "";

        Object data = json.get("data");
        StringBuilder textBuilder = new StringBuilder();

        if (data instanceof JSONObject) {
            textBuilder.append(((JSONObject) data).optString("text"));
        } else if (data instanceof JSONArray) {
            JSONArray arr = (JSONArray) data;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                textBuilder.append(item.optString("text")).append("\n");
            }
        }

        return textBuilder.toString();
    }
    private String extractValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
