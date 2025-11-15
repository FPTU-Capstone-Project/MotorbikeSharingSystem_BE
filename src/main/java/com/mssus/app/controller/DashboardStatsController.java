//package com.mssus.app.controller;
//
//import com.mssus.app.dto.response.MessageResponse;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.media.Content;
//import io.swagger.v3.oas.annotations.media.Schema;
//import io.swagger.v3.oas.annotations.responses.ApiResponse;
//import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import io.swagger.v3.oas.annotations.security.SecurityRequirement;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Slf4j
//@RestController
//@RequestMapping("/api/v1/dashboard")
//@RequiredArgsConstructor
//@Tag(name = "Dashboard", description = "Dashboard statistics endpoints")
//@SecurityRequirement(name = "bearerAuth")
//@PreAuthorize("hasRole('ADMIN')")
//public class DashboardStatsController {
//
//    @Operation(
//            summary = "Get dashboard statistics",
//            description = "Get comprehensive dashboard statistics for admin panel"
//    )
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Dashboard statistics retrieved successfully"),
//            @ApiResponse(responseCode = "401", description = "Unauthorized"),
//            @ApiResponse(responseCode = "403", description = "Forbidden - Admin role required")
//    })
//    @GetMapping("/stats")
//    public ResponseEntity<Map<String, Object>> getDashboardStats() {
//        log.info("Fetching dashboard statistics");
//
//        // Return default/empty stats structure to prevent frontend errors
//        // TODO: Implement actual service call to get real statistics
//        Map<String, Object> stats = new HashMap<>();
//        stats.put("totalUsers", 0);
//        stats.put("activeTrips", 0);
//        stats.put("totalRevenue", 0);
//        stats.put("averageResponseTimeMinutes", 0);
//        stats.put("userGrowthPercentage", 0);
//        stats.put("tripGrowthPercentage", 0);
//        stats.put("revenueGrowthPercentage", 0);
//        stats.put("responseTimeChangeSeconds", 0);
//        stats.put("newUsersThisWeek", 0);
//        stats.put("sharedTripsCount", 0);
//        stats.put("revenueThisWeek", 0);
//        stats.put("responseTimeDescription", "Chưa có dữ liệu");
//        stats.put("monthlyRevenueData", new ArrayList<>());
//        stats.put("rideStatusDistribution", new ArrayList<>());
//        stats.put("hourlyPerformanceData", new ArrayList<>());
//        stats.put("recentActivity", new ArrayList<>());
//
//        return ResponseEntity.ok(stats);
//    }
//}
//
