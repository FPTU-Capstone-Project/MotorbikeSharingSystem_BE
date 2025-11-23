package com.mssus.app.controller;

import com.mssus.app.dto.response.wallet.CommissionReportResponse;
import com.mssus.app.dto.response.wallet.DashboardResponse;
import com.mssus.app.dto.response.wallet.TopUpTrendResponse;
import com.mssus.app.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet Reports", description = "Wallet reporting and analytics endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public class ReportController {

    private final ReportService reportService;

    @Operation(
            summary = "Wallet dashboard statistics",
            description = "Get high-level wallet statistics for dashboard display"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DashboardResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin or Analyst role required")
    })
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @Parameter(description = "Start date (yyyy-MM-dd)") @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "End date (yyyy-MM-dd)") @RequestParam(required = false) LocalDate endDate) {
        log.info("Get wallet dashboard statistics start: {}, end: {}", startDate, endDate);

        DashboardResponse response = reportService.getDashboardStats(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Top-up trends analysis",
            description = "Analyze top-up transaction trends over a period"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trend data retrieved successfully",
                    content = @Content(schema = @Schema(implementation = TopUpTrendResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin or Analyst role required")
    })
    @GetMapping("/topup-trends")
    public ResponseEntity<TopUpTrendResponse> getTopUpTrends(
            @Parameter(description = "Start date for trend analysis", required = true)
            @RequestParam LocalDate startDate,
            @Parameter(description = "End date for trend analysis", required = true)
            @RequestParam LocalDate endDate,
            @Parameter(description = "Grouping interval (daily, weekly, monthly)")
            @RequestParam(defaultValue = "daily") String interval,
            @Parameter(description = "Filter by payment method")
            @RequestParam(required = false) String paymentMethod) {
        log.info("Get top-up trends - startDate: {}, endDate: {}, interval: {}, paymentMethod: {}",
                startDate, endDate, interval, paymentMethod);

        TopUpTrendResponse response = reportService.getTopUpTrends(
                startDate, endDate, interval, paymentMethod);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Commission report",
            description = "Generate detailed commission report for a period"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Commission report generated successfully",
                    content = @Content(schema = @Schema(implementation = CommissionReportResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin or Analyst role required")
    })
    @GetMapping("/commission")
    public ResponseEntity<CommissionReportResponse> getCommissionReport(
            @Parameter(description = "Start date for commission report", required = true)
            @RequestParam LocalDate startDate,
            @Parameter(description = "End date for commission report", required = true)
            @RequestParam LocalDate endDate,
            @Parameter(description = "Filter by driver ID")
            @RequestParam(required = false) Integer driverId) {
        log.info("Get commission report - startDate: {}, endDate: {}, driverId: {}",
                startDate, endDate, driverId);

        CommissionReportResponse response = reportService.getCommissionReport(
                startDate, endDate, driverId);
        return ResponseEntity.ok(response);
    }
}
