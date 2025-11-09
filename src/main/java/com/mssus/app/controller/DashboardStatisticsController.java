package com.mssus.app.controller;

import com.mssus.app.dto.response.DashboardStatsResponse;
import com.mssus.app.service.DashboardStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard Statistics", description = "Dashboard statistics and analytics for administrators")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class DashboardStatisticsController {

    private final DashboardStatisticsService dashboardStatisticsService;

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get dashboard statistics", 
               description = "Returns comprehensive dashboard statistics including users, trips, revenue, and charts data")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        DashboardStatsResponse stats = dashboardStatisticsService.getDashboardStatistics();
        return ResponseEntity.ok(stats);
    }
}

