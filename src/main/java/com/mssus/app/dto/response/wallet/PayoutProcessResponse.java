package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for payout processing operation")
public class PayoutProcessResponse {

    @Schema(description = "Payout reference ID", example = "PAYOUT-123456")
    private String payoutRef;

    @Schema(description = "Payout amount", example = "500000")
    private BigDecimal amount;

    @Schema(description = "Status", example = "PROCESSING")
    private String status;

    @Schema(description = "Evidence URL (if uploaded)", example = "https://cloudinary.com/evidence.jpg")
    private String evidenceUrl;

    @Schema(description = "Notes or additional information", example = "Transfer completed via bank transfer")
    private String notes;

    @Schema(description = "Processed at timestamp", example = "2025-01-15T10:30:00")
    private LocalDateTime processedAt;
}


