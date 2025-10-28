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
@Schema(description = "Request to reject a refund by staff/admin")
public class RejectRefundRequestDto {

    @NotNull(message = "Refund request ID is required")
    @Schema(description = "ID of the refund request to reject", example = "123", required = true)
    private Integer refundRequestId;

    @NotNull(message = "Rejection reason is required")
    @Schema(description = "Reason for rejecting the refund request", 
            example = "User did not complete the ride - full fare is applicable",
            required = true)
    private String rejectionReason;
}





