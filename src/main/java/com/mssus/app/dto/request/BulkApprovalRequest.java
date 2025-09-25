package com.mssus.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk approval request")
public class BulkApprovalRequest {

    @NotEmpty(message = "Verification IDs are required")
    @Schema(description = "List of verification IDs to approve", example = "[1, 2, 3]")
    private List<Integer> verificationIds;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Bulk approval notes", example = "Batch approved after document review")
    private String notes;
}