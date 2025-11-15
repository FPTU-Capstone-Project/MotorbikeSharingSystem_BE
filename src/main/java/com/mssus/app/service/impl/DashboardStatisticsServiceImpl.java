package com.mssus.app.service.impl;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.dto.response.DashboardStatsResponse;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.Transaction;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.repository.SosAlertRepository;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.service.DashboardStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatisticsServiceImpl implements DashboardStatisticsService {

    private final UserRepository userRepository;
    private final SharedRideRepository sharedRideRepository;
    private final TransactionRepository transactionRepository;
    private final SosAlertRepository sosAlertRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStatistics() {
        // Total Users
        long totalUsers = userRepository.count();
        
        // Active Trips (ONGOING status)
        long activeTrips = sharedRideRepository.countByDriverDriverIdAndStatus(null, SharedRideStatus.ONGOING);
        // Also count rides with SCHEDULED status as active
        long scheduledTrips = sharedRideRepository.findAll().stream()
            .filter(r -> r.getStatus() == SharedRideStatus.SCHEDULED)
            .count();
        activeTrips += scheduledTrips;
        
        // Total Revenue (sum of all SUCCESS transactions of type CAPTURE_FARE)
        BigDecimal totalRevenue = transactionRepository.findAll().stream()
            .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
            .filter(t -> t.getType() == TransactionType.CAPTURE_FARE || 
                        t.getType() == TransactionType.TOPUP) // Include topups as revenue
            .map(Transaction::getAmount)
            .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Average Response Time (from SOS alerts)
        List<com.mssus.app.entity.SosAlert> resolvedAlerts = sosAlertRepository.findAll().stream()
            .filter(alert -> alert.getResolvedAt() != null && alert.getCreatedAt() != null)
            .collect(Collectors.toList());
        
        BigDecimal averageResponseTimeMinutes = BigDecimal.ZERO;
        if (!resolvedAlerts.isEmpty()) {
            long totalMinutes = resolvedAlerts.stream()
                .mapToLong(alert -> {
                    LocalDateTime createdAt = alert.getCreatedAt();
                    LocalDateTime resolvedAt = alert.getResolvedAt();
                    if (createdAt != null && resolvedAt != null) {
                        return ChronoUnit.MINUTES.between(createdAt, resolvedAt);
                    }
                    return 0;
                })
                .sum();
            
            averageResponseTimeMinutes = BigDecimal.valueOf(totalMinutes)
                .divide(BigDecimal.valueOf(resolvedAlerts.size()), 1, RoundingMode.HALF_UP);
        }
        
        // Growth calculations (simplified - compare last week vs previous week)
        LocalDate now = LocalDate.now();
        LocalDate weekAgo = now.minusWeeks(1);
        LocalDate twoWeeksAgo = now.minusWeeks(2);
        
        long usersThisWeek = userRepository.findAll().stream()
            .filter(u -> u.getCreatedAt() != null && 
                        u.getCreatedAt().toLocalDate().isAfter(weekAgo))
            .count();
        
        long usersLastWeek = userRepository.findAll().stream()
            .filter(u -> u.getCreatedAt() != null && 
                        u.getCreatedAt().toLocalDate().isAfter(twoWeeksAgo) &&
                        u.getCreatedAt().toLocalDate().isBefore(weekAgo))
            .count();
        
        BigDecimal userGrowthPercentage = usersLastWeek > 0
            ? BigDecimal.valueOf(usersThisWeek - usersLastWeek)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(usersLastWeek), 1, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        // Shared trips count
        long sharedTripsCount = sharedRideRepository.findAll().stream()
            .filter(r -> r.getSharedRideRequest() != null)
            .count();
        
        // Revenue this week
        BigDecimal revenueThisWeek = transactionRepository.findAll().stream()
            .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
            .filter(t -> t.getType() == TransactionType.CAPTURE_FARE || 
                        t.getType() == TransactionType.TOPUP)
            .filter(t -> t.getCreatedAt() != null && 
                        t.getCreatedAt().toLocalDate().isAfter(weekAgo))
            .map(Transaction::getAmount)
            .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Simplified growth calculations for trips and revenue
        BigDecimal tripGrowthPercentage = BigDecimal.valueOf(8.2); // Placeholder
        BigDecimal revenueGrowthPercentage = BigDecimal.valueOf(23.1); // Placeholder
        BigDecimal responseTimeChangeSeconds = BigDecimal.valueOf(-15); // Placeholder
        
        // Monthly Revenue Data (last 8 months)
        List<DashboardStatsResponse.MonthlyRevenueData> monthlyData = generateMonthlyRevenueData();
        
        // Ride Status Distribution
        List<DashboardStatsResponse.RideStatusDistribution> rideStatusData = generateRideStatusDistribution();
        
        // Hourly Performance Data
        List<DashboardStatsResponse.HourlyPerformanceData> hourlyData = generateHourlyPerformanceData();
        
        // Recent Activity (simplified - get recent transactions and rides)
        List<DashboardStatsResponse.RecentActivityItem> recentActivity = generateRecentActivity();
        
        return DashboardStatsResponse.builder()
            .totalUsers(totalUsers)
            .activeTrips(activeTrips)
            .totalRevenue(totalRevenue)
            .averageResponseTimeMinutes(averageResponseTimeMinutes)
            .userGrowthPercentage(userGrowthPercentage)
            .tripGrowthPercentage(tripGrowthPercentage)
            .revenueGrowthPercentage(revenueGrowthPercentage)
            .responseTimeChangeSeconds(responseTimeChangeSeconds)
            .newUsersThisWeek(usersThisWeek)
            .sharedTripsCount(sharedTripsCount)
            .revenueThisWeek(revenueThisWeek)
            .responseTimeDescription("Hỗ trợ khẩn cấp")
            .monthlyRevenueData(monthlyData)
            .rideStatusDistribution(rideStatusData)
            .hourlyPerformanceData(hourlyData)
            .recentActivity(recentActivity)
            .build();
    }
    
    private List<DashboardStatsResponse.MonthlyRevenueData> generateMonthlyRevenueData() {
        List<DashboardStatsResponse.MonthlyRevenueData> data = new ArrayList<>();
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        
        // Get transactions grouped by month
        List<Transaction> allTransactions = transactionRepository.findAll().stream()
            .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
            .filter(t -> t.getType() == TransactionType.CAPTURE_FARE || 
                        t.getType() == TransactionType.TOPUP)
            .collect(Collectors.toList());
        
        // Get last 8 months from current date
        LocalDate now = LocalDate.now();
        
        for (int i = 0; i < 8; i++) {
            // Calculate month index (going back from current month)
            int monthsBack = 7 - i;
            LocalDate targetDate = now.minusMonths(monthsBack);
            int monthIndex = targetDate.getMonthValue();
            int year = targetDate.getYear();
            String monthName = monthNames[monthIndex - 1];
            
            LocalDate monthStart = LocalDate.of(year, monthIndex, 1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            
            BigDecimal monthRevenue = allTransactions.stream()
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> {
                    LocalDate transDate = t.getCreatedAt().toLocalDate();
                    return !transDate.isBefore(monthStart) && !transDate.isAfter(monthEnd);
                })
                .map(Transaction::getAmount)
                .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long monthRides = sharedRideRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> {
                    LocalDate rideDate = r.getCreatedAt().toLocalDate();
                    return !rideDate.isBefore(monthStart) && !rideDate.isAfter(monthEnd);
                })
                .count();
            
            long monthUsers = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null)
                .filter(u -> {
                    LocalDate userDate = u.getCreatedAt().toLocalDate();
                    return !userDate.isBefore(monthStart) && !userDate.isAfter(monthEnd);
                })
                .count();
            
            data.add(DashboardStatsResponse.MonthlyRevenueData.builder()
                .month(monthName)
                .revenue(monthRevenue)
                .rides(monthRides)
                .users(monthUsers)
                .build());
        }
        
        return data;
    }
    
    private List<DashboardStatsResponse.RideStatusDistribution> generateRideStatusDistribution() {
        List<SharedRide> allRides = sharedRideRepository.findAll();
        long totalRides = allRides.size();
        
        if (totalRides == 0) {
            return List.of();
        }
        
        long completed = allRides.stream()
            .filter(r -> r.getStatus() == SharedRideStatus.COMPLETED)
            .count();
        
        long ongoing = allRides.stream()
            .filter(r -> r.getStatus() == SharedRideStatus.ONGOING || 
                        r.getStatus() == SharedRideStatus.SCHEDULED)
            .count();
        
        long cancelled = allRides.stream()
            .filter(r -> r.getStatus() == SharedRideStatus.CANCELLED)
            .count();
        
        long shared = allRides.stream()
            .filter(r -> r.getSharedRideRequest() != null)
            .count();
        
        List<DashboardStatsResponse.RideStatusDistribution> distribution = new ArrayList<>();
        
        if (completed > 0) {
            distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
                .status("COMPLETED")
                .statusLabel("Đã hoàn thành")
                .count(completed)
                .percentage(BigDecimal.valueOf(completed)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
                .color("#059669")
                .build());
        }
        
        if (ongoing > 0) {
            distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
                .status("ONGOING")
                .statusLabel("Đang thực hiện")
                .count(ongoing)
                .percentage(BigDecimal.valueOf(ongoing)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
                .color("#2563EB")
                .build());
        }
        
        if (cancelled > 0) {
            distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
                .status("CANCELLED")
                .statusLabel("Đã hủy")
                .count(cancelled)
                .percentage(BigDecimal.valueOf(cancelled)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
                .color("#DC2626")
                .build());
        }
        
        if (shared > 0) {
            distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
                .status("SHARED")
                .statusLabel("Đi chung")
                .count(shared)
                .percentage(BigDecimal.valueOf(shared)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
                .color("#7C3AED")
                .build());
        }
        
        return distribution;
    }
    
    private List<DashboardStatsResponse.HourlyPerformanceData> generateHourlyPerformanceData() {
        List<DashboardStatsResponse.HourlyPerformanceData> data = new ArrayList<>();
        
        for (int hour = 6; hour <= 23; hour++) {
            LocalTime hourStart = LocalTime.of(hour, 0);
            LocalTime hourEnd = LocalTime.of(hour, 59);
            
            long rides = sharedRideRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> {
                    LocalTime rideTime = r.getCreatedAt().toLocalTime();
                    return !rideTime.isBefore(hourStart) && !rideTime.isAfter(hourEnd);
                })
                .count();
            
            BigDecimal revenue = transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .filter(t -> t.getType() == TransactionType.CAPTURE_FARE || 
                            t.getType() == TransactionType.TOPUP)
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> {
                    LocalTime transTime = t.getCreatedAt().toLocalTime();
                    return !transTime.isBefore(hourStart) && !transTime.isAfter(hourEnd);
                })
                .map(Transaction::getAmount)
                .filter(amount -> amount != null && amount.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            data.add(DashboardStatsResponse.HourlyPerformanceData.builder()
                .hour(hour + "h")
                .rides(rides)
                .revenue(revenue)
                .build());
        }
        
        return data;
    }
    
    private List<DashboardStatsResponse.RecentActivityItem> generateRecentActivity() {
        // Get recent transactions and rides
        List<Transaction> recentTransactions = transactionRepository.findAll().stream()
            .sorted((a, b) -> {
                if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            })
            .limit(5)
            .collect(Collectors.toList());
        
        List<DashboardStatsResponse.RecentActivityItem> activities = new ArrayList<>();
        
        for (Transaction t : recentTransactions) {
            String type = t.getType() == TransactionType.CAPTURE_FARE ? "ride_completed" : "payment";
            String badgeLabel = t.getType() == TransactionType.CAPTURE_FARE ? "Chuyến đi" : "Nạp tiền";
            String user = t.getActorUser() != null ? 
                (t.getActorUser().getFullName() != null ? t.getActorUser().getFullName() : "User") : 
                "Unknown";
            String description = t.getType() == TransactionType.CAPTURE_FARE ? 
                "đã kết thúc chuyến" : "nạp tiền vào ví";
            String time = t.getCreatedAt() != null ? 
                formatTimeAgo(t.getCreatedAt()) : "Vừa xong";
            String status = t.getStatus() == TransactionStatus.SUCCESS ? "success" : "warning";
            String avatar = user.length() > 0 ? user.substring(0, 1).toUpperCase() : "U";
            
            activities.add(DashboardStatsResponse.RecentActivityItem.builder()
                .type(type)
                .badgeLabel(badgeLabel)
                .user(user)
                .description(description)
                .time(time)
                .status(status)
                .amount(t.getAmount())
                .avatar(avatar)
                .build());
        }
        
        return activities;
    }
    
    private String formatTimeAgo(LocalDateTime dateTime) {
        long minutesAgo = ChronoUnit.MINUTES.between(dateTime, LocalDateTime.now());
        if (minutesAgo < 1) return "Vừa xong";
        if (minutesAgo < 60) return minutesAgo + " phút trước";
        long hoursAgo = minutesAgo / 60;
        if (hoursAgo < 24) return hoursAgo + " giờ trước";
        long daysAgo = hoursAgo / 24;
        return daysAgo + " ngày trước";
    }
}

