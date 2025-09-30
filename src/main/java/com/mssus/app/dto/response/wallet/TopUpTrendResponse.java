package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Top-up trends analysis")
public class TopUpTrendResponse {

    @Schema(description = "Data points for trend chart")
    private List<TrendDataPoint> dataPoints;

    @Schema(description = "Total top-up amount in period", example = "150000000")
    private BigDecimal totalAmount;

    @Schema(description = "Average top-up per transaction", example = "100000")
    private BigDecimal avgTopUpAmount;

    @Schema(description = "Most popular payment method", example = "MOMO")
    private String mostPopularPaymentMethod;

    @Schema(description = "Growth rate percentage", example = "15.5")
    private Double growthRate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        @Schema(description = "Date")
        private LocalDate date;

        @Schema(description = "Total amount")
        private BigDecimal amount;

        @Schema(description = "Transaction count")
        private Integer count;
    }
}