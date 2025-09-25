package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Verification decision request")
public class VerificationDecisionRequest {

    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    @Schema(description = "Reason for rejection (required if rejecting)", example = "Document is not clear")
    private String rejectionReason;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    @Schema(description = "Additional notes from admin", example = "Approved after manual review")
    private String notes;
}
