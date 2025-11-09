package com.mssus.app.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class DashboardStatsResponse {
    // Summary Statistics
    Long totalUsers;
    Long activeTrips;
    BigDecimal totalRevenue;
    BigDecimal averageResponseTimeMinutes;
    
    // Growth/Change percentages
    BigDecimal userGrowthPercentage;
    BigDecimal tripGrowthPercentage;
    BigDecimal revenueGrowthPercentage;
    BigDecimal responseTimeChangeSeconds;
    
    // Details
    Long newUsersThisWeek;
    Long sharedTripsCount;
    BigDecimal revenueThisWeek;
    String responseTimeDescription;
    
    // Chart Data
    List<MonthlyRevenueData> monthlyRevenueData;
    List<RideStatusDistribution> rideStatusDistribution;
    List<HourlyPerformanceData> hourlyPerformanceData;
    
    // Recent Activity
    List<RecentActivityItem> recentActivity;
    
    @Value
    @Builder
    public static class MonthlyRevenueData {
        String month;
        BigDecimal revenue;
        Long rides;
        Long users;
    }
    
    @Value
    @Builder
    public static class RideStatusDistribution {
        String status;
        String statusLabel;
        Long count;
        BigDecimal percentage;
        String color;
    }
    
    @Value
    @Builder
    public static class HourlyPerformanceData {
        String hour;
        Long rides;
        BigDecimal revenue;
    }
    
    @Value
    @Builder
    public static class RecentActivityItem {
        String type;
        String badgeLabel;
        String user;
        String description;
        String time;
        String status;
        BigDecimal amount;
        String avatar;
    }
}

