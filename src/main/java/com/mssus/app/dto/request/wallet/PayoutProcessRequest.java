package com.mssus.app.dto.request.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Schema(description = "Request to process a payout (admin only)")
public class PayoutProcessRequest {

    @Schema(description = "Evidence file (screenshot, receipt, transaction ID) - required for completion", example = "file")
    private MultipartFile evidenceFile;

    @Schema(description = "Notes or additional information about the payout processing", example = "Transfer completed via bank transfer, Transaction ID: TXN123456")
    private String notes;

    @Schema(description = "Failure reason - required for fail operation", example = "Invalid bank account number")
    private String reason;
}










