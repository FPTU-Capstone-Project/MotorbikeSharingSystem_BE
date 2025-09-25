package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Student verification response")
public class StudentVerificationResponse {

    @JsonProperty("verification_id")
    @Schema(description = "Verification ID", example = "1")
    private Integer verificationId;

    @JsonProperty("user_id")
    @Schema(description = "User ID", example = "1")
    private Integer userId;

    @JsonProperty("full_name")
    @Schema(description = "Student full name", example = "Nguyen Van A")
    private String fullName;

    @Schema(description = "Email address", example = "student@example.edu")
    private String email;

    @Schema(description = "Phone number", example = "0901234567")
    private String phone;

    @JsonProperty("student_id")
    @Schema(description = "Student ID", example = "SV001")
    private String studentId;

    @Schema(description = "Verification status", example = "pending")
    private String status;

    @JsonProperty("document_url")
    @Schema(description = "Student ID card image URL")
    private String documentUrl;

    @JsonProperty("document_type")
    @Schema(description = "Document type", example = "image")
    private String documentType;

    @JsonProperty("rejection_reason")
    @Schema(description = "Rejection reason if rejected")
    private String rejectionReason;

    @JsonProperty("verified_by")
    @Schema(description = "Admin who verified")
    private String verifiedBy;

    @JsonProperty("verified_at")
    @Schema(description = "Verification date")
    private LocalDateTime verifiedAt;

    @JsonProperty("created_at")
    @Schema(description = "Submission date")
    private LocalDateTime createdAt;
}