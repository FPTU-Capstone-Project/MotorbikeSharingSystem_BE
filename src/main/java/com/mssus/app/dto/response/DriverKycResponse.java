package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Driver KYC response")
public class DriverKycResponse {

    @JsonProperty("user_id")
    @Schema(description = "User ID", example = "1")
    private Integer userId;

    @JsonProperty("full_name")
    @Schema(description = "Driver full name", example = "Nguyen Van B")
    private String fullName;

    @Schema(description = "Email address", example = "driver@example.com")
    private String email;

    @Schema(description = "Phone number", example = "0901234568")
    private String phone;

    @JsonProperty("license_number")
    @Schema(description = "License number", example = "LIC123456")
    private String licenseNumber;

    @JsonProperty("driver_status")
    @Schema(description = "Driver profile status", example = "pending")
    private String driverStatus;

    @JsonProperty("verifications")
    @Schema(description = "List of verifications for this driver")
    private List<VerificationInfo> verifications;

    @JsonProperty("created_at")
    @Schema(description = "Driver profile creation date")
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Verification information")
    public static class VerificationInfo {

        @JsonProperty("verification_id")
        private Integer verificationId;

        private String type;
        private String status;

        @JsonProperty("document_url")
        private String documentUrl;

        @JsonProperty("document_type")
        private String documentType;

        @JsonProperty("rejection_reason")
        private String rejectionReason;

        @JsonProperty("verified_by")
        private String verifiedBy;

        @JsonProperty("verified_at")
        private LocalDateTime verifiedAt;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }
}