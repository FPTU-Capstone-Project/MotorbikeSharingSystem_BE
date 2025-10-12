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
    public boolean verifyDriverLicense(User user, List<MultipartFile> documents) {
        if (documents == null || documents.isEmpty()) {
            throw ValidationException.of("At least one document to verify");
        }

        StringBuilder combinedText = new StringBuilder();
        String extractedName = "";
        String extractedId = "";
        String extractedDob = "";
        String extractedDoe = "";
        String extractedType = "";

        for (MultipartFile doc : documents) {
            String ocrJson = analyzeDocument(doc, VerificationType.DRIVER_LICENSE);
            JSONObject json = new JSONObject(ocrJson);
            log.debug("FPT.AI OCR raw JSON: {}", json.toString(2));

            if (json.has("data") && json.get("data") instanceof JSONArray) {
                JSONArray arr = json.getJSONArray("data");
                if (!arr.isEmpty()) {
                    JSONObject data = arr.getJSONObject(0);

                    extractedName = data.optString("name", extractedName);
                    extractedId = data.optString("id", extractedId);
                    extractedDob = data.optString("dob", extractedDob);
                    extractedDoe = data.optString("doe", extractedDoe);
                    extractedType = data.optString("type", extractedType);

                    log.info("""
                        === FPT.AI Driver License OCR ===
                        🔸 Name: {}
                        🔸 ID: {}
                        🔸 DOB: {}
                        🔸 DOE (expiry): {}
                        🔸 Type: {}
                        """,
                            extractedName, extractedId, extractedDob, extractedDoe, extractedType
                    );

                    // gộp text để regex fallback
                    combinedText.append(extractedName).append("\n")
                            .append(extractedDob).append("\n")
                            .append(extractedId).append("\n")
                            .append(extractedDoe).append("\n");
                    continue;
                }
            }

            // fallback nếu không có structured data
            combinedText.append(extractOcrText(json)).append("\n");
        }

        String text = combinedText.toString().trim();
        log.info("📜 OCR Combined Text (Driver License):\n{}", text);

        // Nếu structured JSON không có name, dùng regex fallback
        if (extractedName.isEmpty()) {
            // Thường tên nằm dòng đầu tiên của text OCR
            String[] lines = text.split("\\r?\\n");
            if (lines.length > 0 && lines[0].matches("^[A-ZÀ-Ỹ\\s]+$")) {
                extractedName = lines[0].trim();
                log.info("🔁 Fallback lấy tên từ dòng đầu OCR: {}", extractedName);
            } else {
                extractedName = extractValue(text, "(?i)(Họ và tên|Full name|Họ tên)[:\\s]+([A-ZÀ-Ỹ\\s]+)");
            }
        }

        // Nếu ID, DOB, DOE vẫn rỗng → thử regex
        if (extractedId.isEmpty()) {
            extractedId = extractValue(text, "(?i)(Số|No)[:\\s]*([A-Z0-9]+)");
        }
        if (extractedDob.isEmpty()) {
            extractedDob = extractValue(text, "(?i)\\b(\\d{2}/\\d{2}/\\d{4})\\b");
        }
        if (extractedDoe.isEmpty()) {
            extractedDoe = extractValue(text, "(?i)(Có giá trị đến|Ngày hết hạn)[:\\s]*(\\d{2}/\\d{2}/\\d{4}|KHÔNG THỜI HẠN)");
        }

        // === VALIDATION ===
        if (extractedName.isEmpty() || !user.getFullName().equalsIgnoreCase(extractedName)) {
            log.warn("❌ Tên trên GPLX không khớp: expected={}, found={}", user.getFullName(), extractedName);
            return false;
        }

        if (extractedId.isEmpty()) {
            log.warn("❌ Không tìm thấy số GPLX");
            return false;
        }

        if (extractedDoe.equalsIgnoreCase("KHÔNG THỜI HẠN")) {
            log.info("✅ GPLX không thời hạn — hợp lệ cho người dùng {}", user.getEmail());
            return true;
        }

        if (extractedDoe.isEmpty()) {
            log.warn("❌ Không tìm thấy ngày hết hạn GPLX");
            return false;
        }

        try {
            LocalDate expiry = LocalDate.parse(extractedDoe, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            if (expiry.isBefore(LocalDate.now())) {
                log.warn("❌ GPLX đã hết hạn: {}", extractedDoe);
                return false;
            }
        } catch (Exception e) {
            log.warn("⚠️ Không thể parse ngày hết hạn: {}", extractedDoe);
        }

        log.info("✅ GPLX hợp lệ cho người dùng {}", user.getEmail());
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
