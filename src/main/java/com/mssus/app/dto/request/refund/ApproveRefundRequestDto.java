package com.mssus.app.dto.request.refund;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to approve a refund by staff/admin")
public class ApproveRefundRequestDto {

    @NotNull(message = "Refund request ID is required")
    @Schema(description = "ID of the refund request to approve", example = "123", required = true)
    private Integer refundRequestId;

    @Schema(description = "Notes or comments from the reviewer", example = "Approved - Valid complaint regarding service quality")
    private String reviewNotes;

    @Schema(description = "PSP reference for payment processing", example = "PSP-2024-001-789")
    private String pspRef;
}





