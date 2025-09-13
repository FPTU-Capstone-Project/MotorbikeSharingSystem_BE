package com.mssus.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Verification response")
public class VerificationResponse {

    @JsonProperty("verification_id")
    @Schema(description = "Verification ID", example = "1001")
    private Integer verificationId;

    @Schema(description = "Verification status", example = "pending")
    private String status;

    @Schema(description = "Verification type", example = "student_id")
    private String type;

    @JsonProperty("document_url")
    @Schema(description = "Document URL", example = "https://cdn.example.com/verification/1001.jpg")
    private String documentUrl;

    @JsonProperty("rejection_reason")
    @Schema(description = "Rejection reason (if rejected)")
    private String rejectionReason;
}
