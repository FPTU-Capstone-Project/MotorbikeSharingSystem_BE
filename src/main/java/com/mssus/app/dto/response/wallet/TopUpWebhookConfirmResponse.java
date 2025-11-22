package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
@Schema(description = "Kết quả xác minh giao dịch sau khi nhận webhook PayOS")
public class TopUpWebhookConfirmResponse {

    @Schema(description = "Order code do PayOS trả về", example = "1715339322")
    String orderCode;

    @Schema(description = "Số tiền gắn với order code", example = "150000")
    BigDecimal amount;

    @Schema(description = "ID giao dịch trong hệ thống", example = "1234")
    Long transactionId;

    @Schema(description = "Trạng thái transaction trong ledger", example = "SUCCESS")
    String transactionStatus;

    @Schema(description = "Thông điệp tóm tắt")
    String message;
}


