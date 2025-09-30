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
@Schema(description = "Commission report for period")
public class CommissionReportResponse {

    @Schema(description = "Report period start")
    private LocalDate periodStart;

    @Schema(description = "Report period end")
    private LocalDate periodEnd;

    @Schema(description = "Total commission collected", example = "5000000")
    private BigDecimal totalCommission;

    @Schema(description = "Total bookings processed", example = "500")
    private Integer totalBookings;

    @Schema(description = "Average commission per booking", example = "10000")
    private BigDecimal avgCommissionPerBooking;

    @Schema(description = "Commission breakdown by driver")
    private List<DriverCommission> driverCommissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverCommission {
        @Schema(description = "Driver ID")
        private Integer driverId;

        @Schema(description = "Driver name")
        private String driverName;

        @Schema(description = "Total commission paid")
        private BigDecimal commissionPaid;

        @Schema(description = "Number of trips")
        private Integer tripCount;

        @Schema(description = "Total earnings")
        private BigDecimal totalEarnings;
    }
}