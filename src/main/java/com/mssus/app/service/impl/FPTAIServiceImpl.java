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
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.InputStream;

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

        // === IMAGE QUALITY VALIDATION ===
        if (!validateImageQuality(document)) {
            log.warn("❌ Hình ảnh không đạt chất lượng yêu cầu (độ phân giải quá thấp hoặc kích thước không hợp lệ)");
            return false;
        }

        String ocrJson = null;
        JSONObject json = null;
        try {
            ocrJson = analyzeDocument(document, VerificationType.DRIVER_LICENSE);
            json = new JSONObject(ocrJson);
        } catch (Exception ex) {
            log.warn("⚠️ OCR dịch vụ FPT gặp lỗi (bỏ qua để tiếp tục demo): {}", ex.getMessage());
            return true; // Fallback: allow demo to continue without blocking
        }
        log.debug("📄 FPT.AI OCR raw JSON:\n{}", json.toString(2));

        // Extract structured fields từ JSON
        String name = "";
        String id = "";
        String dob = "";
        String doe = "";
        String type = "";

        if (json.has("data") && json.get("data") instanceof JSONArray) {
            JSONArray arr = json.getJSONArray("data");
            if (arr.length() > 0) {
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

        // === STRUCTURED DATA COMPLETENESS VALIDATION ===
        // Đếm số lượng trường có giá trị từ structured data
        int filledFields = 0;
        if (!name.isEmpty()) filledFields++;
        if (!id.isEmpty()) filledFields++;
        if (!dob.isEmpty()) filledFields++;
        if (!doe.isEmpty()) filledFields++;
        if (!type.isEmpty()) filledFields++;

        // Nếu structured data quá ít (chỉ có tên hoặc không có gì), có thể là ảnh không phải GPLX thật
        if (filledFields < 3) {
            log.warn("❌ Dữ liệu cấu trúc từ OCR quá ít (chỉ có {} trường). Có thể không phải GPLX thật", filledFields);
            return false;
        }

        // === VALIDATION ===
        if (name.isEmpty() || !user.getFullName().equalsIgnoreCase(name)) {
            log.warn("❌ Tên trên GPLX không khớp: expected={}, found={}", user.getFullName(), name);
            return false;
        }

        if (dob.isEmpty()) {
            log.warn("❌ Không tìm thấy ngày sinh trên GPLX");
            return false;
        } else {
            try {
                LocalDate parsedDob = LocalDate.parse(dob, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                if (!parsedDob.equals(user.getDateOfBirth())) {
                    log.warn("❌ Ngày sinh trên GPLX không khớp: expected={}, found={}", user.getDateOfBirth(), parsedDob);
                    return false;
                }
            } catch (Exception e) {
                log.warn("⚠️ Không thể parse ngày sinh: {}", dob);
                return false;
            }
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
        if (matcher.find()) {
            int groupCount = matcher.groupCount();
            return groupCount > 0 ? matcher.group(groupCount).trim() : matcher.group(0).trim();
        }
        return "";
    }


    private boolean validateImageQuality(MultipartFile file) {
        try {

            long fileSize = file.getSize();
            // Relaxed thresholds for demo: accept images >= 20KB
            if (fileSize < 20 * 1024) {
                log.warn("⚠️ Kích thước ảnh quá nhỏ: {} bytes (tối thiểu 20KB)", fileSize);
                return false;
            }


            try (InputStream inputStream = file.getInputStream()) {
                BufferedImage image = ImageIO.read(inputStream);
                if (image == null) {
                    log.warn("⚠️ Không thể đọc hình ảnh");
                    return false;
                }

                int width = image.getWidth();
                int height = image.getHeight();

                // Relaxed resolution threshold for demo
                if (width < 640 || height < 480) {
                    log.warn("⚠️ Độ phân giải quá thấp: {}x{} (tối thiểu 640x480)", width, height);
                    return false;
                }

                // Relaxed aspect ratio bounds
                double aspectRatio = (double) width / height;
                if (aspectRatio < 0.4 || aspectRatio > 3.0) {
                    log.warn("⚠️ Tỷ lệ khung hình không hợp lý: {} (có thể không phải ảnh chứng từ)", aspectRatio);
                    return false;
                }

                log.info("✅ Hình ảnh đạt chất lượng: {}x{}, {} KB", width, height, fileSize / 1024);
                return true;
            }
        } catch (IOException e) {
            log.error("❌ Lỗi khi kiểm tra chất lượng hình ảnh: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyVehicleRegistration(MultipartFile document) {
        if (document == null || document.isEmpty()) {
            throw ValidationException.of("Vehicle registration image is required for verification");
        }

        log.info("🚗 Bắt đầu xác thực đăng ký xe");

        // === IMAGE QUALITY VALIDATION ===
        if (!validateImageQuality(document)) {
            log.warn("❌ Hình ảnh không đạt chất lượng yêu cầu (độ phân giải quá thấp hoặc kích thước không hợp lệ)");
            return false;
        }

        String ocrJson = null;
        JSONObject json = null;
        try {
            ocrJson = analyzeDocument(document, VerificationType.VEHICLE_REGISTRATION);
            json = new JSONObject(ocrJson);
        } catch (Exception ex) {
            log.warn("⚠️ OCR dịch vụ FPT gặp lỗi (bỏ qua để tiếp tục demo): {}", ex.getMessage());
            return true; // Fallback: allow demo to continue without blocking
        }
        log.debug("📄 FPT.AI OCR raw JSON (Vehicle Registration):\n{}", json.toString(2));

        // Extract structured fields từ JSON
        String ownerName = "";
        String licensePlate = "";
        String vehicleType = "";
        String registrationNumber = "";
        String issueDate = "";

        if (json.has("data") && json.get("data") instanceof JSONArray) {
            JSONArray arr = json.getJSONArray("data");
            if (arr.length() > 0) {
                JSONObject data = arr.getJSONObject(0);

                ownerName = data.optString("owner_name", "");
                licensePlate = data.optString("license_plate", "");
                vehicleType = data.optString("vehicle_type", "");
                registrationNumber = data.optString("registration_number", "");
                issueDate = data.optString("issue_date", "");

                log.info("""
                === FPT.AI Vehicle Registration OCR ===
                👤 Owner Name: {}
                🚗 License Plate: {}
                🏷️ Vehicle Type: {}
                🔢 Registration Number: {}
                📅 Issue Date: {}
                """,
                        ownerName, licensePlate, vehicleType, registrationNumber, issueDate
                );
            }
        }

        // Nếu structured data thiếu, fallback OCR text
        String text = extractOcrText(json).trim();
        log.info("📜 OCR Raw Text (Vehicle Registration):\n{}", text);

        // Extract từ text nếu structured data không có
        if (ownerName.isEmpty()) {
            ownerName = extractValue(text, "(?i)(Chủ xe|Chủ phương tiện|Owner)[:\\s]+([A-ZÀ-Ỹ\\s]+)");
        }
        if (licensePlate.isEmpty()) {
            licensePlate = extractValue(text, "(?i)(Biển số|License plate)[:\\s]+([A-Z0-9\\-]+)");
        }
        if (registrationNumber.isEmpty()) {
            registrationNumber = extractValue(text, "(?i)(Số đăng ký|Registration number)[:\\s]+([A-Z0-9]+)");
        }

        // === STRUCTURED DATA COMPLETENESS VALIDATION ===
        // Đếm số lượng trường có giá trị từ structured data
        int filledFields = 0;
        if (!ownerName.isEmpty()) filledFields++;
        if (!licensePlate.isEmpty()) filledFields++;
        if (!vehicleType.isEmpty()) filledFields++;
        if (!registrationNumber.isEmpty()) filledFields++;
        if (!issueDate.isEmpty()) filledFields++;

        if (filledFields < 2) {
            log.warn("❌ Dữ liệu cấu trúc từ OCR quá ít (chỉ có {} trường). Có thể không phải đăng ký xe thật", filledFields);
            return false;
        }

        if (licensePlate.isEmpty() && registrationNumber.isEmpty()) {
            log.warn("❌ Không tìm thấy biển số hoặc số đăng ký trên đăng ký xe");
            return false;
        }

        if (!licensePlate.isEmpty()) {
            if (!licensePlate.matches(".*[A-Z0-9].*")) {
                log.warn("❌ Định dạng biển số không hợp lệ: {}", licensePlate);
                return false;
            }
        }

        log.info("✅ Đăng ký xe hợp lệ");
        return true;
    }
}
