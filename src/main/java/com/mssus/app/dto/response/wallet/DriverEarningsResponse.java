package com.mssus.app.dto.response.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Driver earnings summary response")
public class DriverEarningsResponse {

    @Schema(description = "Available balance", example = "1500000")
    private BigDecimal availableBalance;

    @Schema(description = "Pending earnings", example = "250000")
    private BigDecimal pendingEarnings;

    @Schema(description = "Total earnings (all-time)", example = "5000000")
    private BigDecimal totalEarnings;

    @Schema(description = "Total trips completed", example = "150")
    private Integer totalTrips;

    @Schema(description = "This month earnings", example = "800000")
    private BigDecimal monthEarnings;

    @Schema(description = "This week earnings", example = "200000")
    private BigDecimal weekEarnings;

    @Schema(description = "Average earnings per trip", example = "33333")
    private BigDecimal avgEarningsPerTrip;

    @Schema(description = "Total commission paid", example = "500000")
    private BigDecimal totalCommissionPaid;
}