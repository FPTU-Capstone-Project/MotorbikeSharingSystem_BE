package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatisticsServiceImpl implements DashboardStatisticsService {

    private final UserRepository userRepository;
    private final SharedRideRepository sharedRideRepository;
    private final TransactionRepository transactionRepository;
    private final SosAlertRepository sosAlertRepository;

    private boolean isRiderFareDebit(Transaction t) {
        return t.getStatus() == TransactionStatus.SUCCESS
                && t.getType() == TransactionType.CAPTURE_FARE
                && t.getDirection() == TransactionDirection.OUT
                && t.getActorKind() == ActorKind.USER
                && t.getAmount() != null
                && t.getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean withinRange(LocalDateTime ts, LocalDateTime start, LocalDateTime end) {
        if (ts == null) return false;
        if (start != null && ts.isBefore(start)) return false;
        if (end != null && !ts.isBefore(end)) return false; // end exclusive
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStatistics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.plusDays(1).atStartOfDay() : null;
        LocalDate weekAgoDate = today.minusWeeks(1);
        LocalDate twoWeeksAgoDate = today.minusWeeks(2);
        LocalDateTime weekAgo = weekAgoDate.atStartOfDay();
        LocalDateTime twoWeeksAgo = twoWeeksAgoDate.atStartOfDay();

        long totalUsers = userRepository.count();

        long activeTrips = sharedRideRepository.countByStatus(SharedRideStatus.ONGOING)
                + sharedRideRepository.countByStatus(SharedRideStatus.SCHEDULED);

        BigDecimal totalRevenue = transactionRepository.findAll().stream()
                .filter(this::isRiderFareDebit)
                .filter(t -> start == null && end == null || withinRange(t.getCreatedAt(), start, end))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Average response time from resolved SOS
        List<com.mssus.app.entity.SosAlert> resolvedAlerts = sosAlertRepository.findAll().stream()
                .filter(alert -> alert.getResolvedAt() != null && alert.getCreatedAt() != null)
                .toList();
        BigDecimal averageResponseTimeMinutes = BigDecimal.ZERO;
        if (!resolvedAlerts.isEmpty()) {
            long totalMinutes = resolvedAlerts.stream()
                    .mapToLong(alert -> ChronoUnit.MINUTES.between(alert.getCreatedAt(), alert.getResolvedAt()))
                    .sum();
            averageResponseTimeMinutes = BigDecimal.valueOf(totalMinutes)
                    .divide(BigDecimal.valueOf(resolvedAlerts.size()), 1, RoundingMode.HALF_UP);
        }

        long usersThisWeek = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null && !u.getCreatedAt().toLocalDate().isBefore(weekAgoDate))
                .count();
        long usersLastWeek = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null &&
                        !u.getCreatedAt().toLocalDate().isBefore(twoWeeksAgoDate) &&
                        u.getCreatedAt().toLocalDate().isBefore(weekAgoDate))
                .count();
        BigDecimal userGrowthPercentage = usersLastWeek > 0
                ? BigDecimal.valueOf(usersThisWeek - usersLastWeek)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(usersLastWeek), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long ridesThisWeek = sharedRideRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().toLocalDate().isBefore(weekAgoDate))
                .count();
        long ridesLastWeek = sharedRideRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null &&
                        !r.getCreatedAt().toLocalDate().isBefore(twoWeeksAgoDate) &&
                        r.getCreatedAt().toLocalDate().isBefore(weekAgoDate))
                .count();
        BigDecimal tripGrowthPercentage = ridesLastWeek > 0
                ? BigDecimal.valueOf(ridesThisWeek - ridesLastWeek)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(ridesLastWeek), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Response time change vs previous period
        BigDecimal responseTimeChangeSeconds = BigDecimal.ZERO;

        long sharedTripsCount = sharedRideRepository.findAll().stream()
                .filter(r -> r.getSharedRideRequest() != null)
                .filter(r -> start == null && end == null || withinRange(r.getCreatedAt(), start, end))
                .count();

        BigDecimal revenueThisWeek;
        BigDecimal revenueLastWeek;
        BigDecimal revenueGrowthPercentage;
        if (start != null || end != null) {
            revenueThisWeek = totalRevenue;
            revenueLastWeek = BigDecimal.ZERO;
            revenueGrowthPercentage = BigDecimal.ZERO;
        } else {
            revenueThisWeek = transactionRepository.findAll().stream()
                    .filter(this::isRiderFareDebit)
                    .filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().toLocalDate().isBefore(weekAgoDate))
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            revenueLastWeek = transactionRepository.findAll().stream()
                    .filter(this::isRiderFareDebit)
                    .filter(t -> t.getCreatedAt() != null &&
                            !t.getCreatedAt().toLocalDate().isBefore(twoWeeksAgoDate) &&
                            t.getCreatedAt().toLocalDate().isBefore(weekAgoDate))
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            revenueGrowthPercentage = revenueLastWeek.compareTo(BigDecimal.ZERO) > 0
                    ? revenueThisWeek.subtract(revenueLastWeek)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(revenueLastWeek, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
        }

        List<DashboardStatsResponse.MonthlyRevenueData> monthlyData = generateMonthlyRevenueData(start, end);
        List<DashboardStatsResponse.RideStatusDistribution> rideStatusData = generateRideStatusDistribution(start, end);
        List<DashboardStatsResponse.HourlyPerformanceData> hourlyData = generateHourlyPerformanceData(start, end);
        List<DashboardStatsResponse.RecentActivityItem> recentActivity = generateRecentActivity(start, end);

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
    
    private List<DashboardStatsResponse.MonthlyRevenueData> generateMonthlyRevenueData(LocalDateTime start, LocalDateTime end) {
        List<DashboardStatsResponse.MonthlyRevenueData> data = new ArrayList<>();
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Fare capture transactions (rider debits only)
        List<Transaction> fareTransactions = transactionRepository.findAll().stream()
            .filter(this::isRiderFareDebit)
            .filter(t -> start == null && end == null || withinRange(t.getCreatedAt(), start, end))
            .collect(Collectors.toList());

        // If a date range is provided and within ~1 month, return daily buckets
        if (start != null && end != null) {
            final LocalDate startDate = start.toLocalDate();
            final LocalDate endDate = end.minusSeconds(1).toLocalDate(); // end exclusive
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (days > 0 && days <= 35) {
                LocalDate cursor = startDate;
                while (!cursor.isAfter(endDate)) {
                    final LocalDate current = cursor;
                    BigDecimal dayRevenue = fareTransactions.stream()
                        .filter(t -> t.getCreatedAt() != null)
                        .filter(t -> t.getCreatedAt().toLocalDate().isEqual(current))
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long dayRides = sharedRideRepository.findAll().stream()
                        .filter(r -> r.getCreatedAt() != null)
                        .filter(r -> r.getCreatedAt().toLocalDate().isEqual(current))
                        .count();
                    long dayUsers = userRepository.findAll().stream()
                        .filter(u -> u.getCreatedAt() != null)
                        .filter(u -> u.getCreatedAt().toLocalDate().isEqual(current))
                        .count();
                    data.add(DashboardStatsResponse.MonthlyRevenueData.builder()
                        .month(current.toString()) // date label
                        .revenue(dayRevenue)
                        .rides(dayRides)
                        .users(dayUsers)
                        .build());
                    cursor = cursor.plusDays(1);
                }
                return data;
            }
        }

        // Default: last 8 months aggregated monthly
        LocalDate now = LocalDate.now();
        int months = 8;
        for (int i = 0; i < months; i++) {
            int monthsBack = months - 1 - i;
            LocalDate targetDate = now.minusMonths(monthsBack);
            int monthIndex = targetDate.getMonthValue();
            int year = targetDate.getYear();
            String monthName = monthNames[monthIndex - 1];

            LocalDate monthStart = LocalDate.of(year, monthIndex, 1);
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

            if (start != null && monthEnd.atStartOfDay().isBefore(start)) {
                continue;
            }
            if (end != null && monthStart.atStartOfDay().isAfter(end.minusDays(1))) {
                continue;
            }

            BigDecimal monthRevenue = fareTransactions.stream()
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> {
                    LocalDate transDate = t.getCreatedAt().toLocalDate();
                    return !transDate.isBefore(monthStart) && !transDate.isAfter(monthEnd);
                })
                .map(Transaction::getAmount)
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
    
    private List<DashboardStatsResponse.RideStatusDistribution> generateRideStatusDistribution(LocalDateTime start, LocalDateTime end) {
        long completed;
        long ongoing;
        long scheduled;
        long cancelled;

        if (start == null && end == null) {
            completed = sharedRideRepository.countByStatus(SharedRideStatus.COMPLETED);
            ongoing = sharedRideRepository.countByStatus(SharedRideStatus.ONGOING);
            scheduled = sharedRideRepository.countByStatus(SharedRideStatus.SCHEDULED);
            cancelled = sharedRideRepository.countByStatus(SharedRideStatus.CANCELLED);
        } else {
            List<SharedRide> rides = sharedRideRepository.findAll().stream()
                    .filter(r -> withinRange(r.getCreatedAt(), start, end))
                    .toList();
            completed = rides.stream().filter(r -> r.getStatus() == SharedRideStatus.COMPLETED).count();
            ongoing = rides.stream().filter(r -> r.getStatus() == SharedRideStatus.ONGOING).count();
            scheduled = rides.stream().filter(r -> r.getStatus() == SharedRideStatus.SCHEDULED).count();
            cancelled = rides.stream().filter(r -> r.getStatus() == SharedRideStatus.CANCELLED).count();
        }

        long totalRides = completed + ongoing + scheduled + cancelled;
        if (totalRides == 0) {
            return List.of();
        }

        List<DashboardStatsResponse.RideStatusDistribution> distribution = new ArrayList<>();

        distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
            .status("SCHEDULED")
            .statusLabel("Đã lên lịch")
            .count(scheduled)
            .percentage(BigDecimal.valueOf(scheduled)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
            .color("#F59E0B")
            .build());

        distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
            .status("ONGOING")
            .statusLabel("Đang thực hiện")
            .count(ongoing)
            .percentage(BigDecimal.valueOf(ongoing)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
            .color("#2563EB")
            .build());

        distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
            .status("COMPLETED")
            .statusLabel("Đã hoàn thành")
            .count(completed)
            .percentage(BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
            .color("#059669")
            .build());

        distribution.add(DashboardStatsResponse.RideStatusDistribution.builder()
            .status("CANCELLED")
            .statusLabel("Đã hủy")
            .count(cancelled)
            .percentage(BigDecimal.valueOf(cancelled)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRides), 1, RoundingMode.HALF_UP))
            .color("#DC2626")
            .build());

        return distribution;
    }
    
    private List<DashboardStatsResponse.HourlyPerformanceData> generateHourlyPerformanceData(LocalDateTime start, LocalDateTime end) {
        List<DashboardStatsResponse.HourlyPerformanceData> data = new ArrayList<>();
        
        for (int hour = 6; hour <= 23; hour++) {
            LocalTime hourStart = LocalTime.of(hour, 0);
            LocalTime hourEnd = LocalTime.of(hour, 59);
            
            long rides = sharedRideRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null)
                .filter(r -> start == null && end == null || withinRange(r.getCreatedAt(), start, end))
                .filter(r -> {
                    LocalTime rideTime = r.getCreatedAt().toLocalTime();
                    return !rideTime.isBefore(hourStart) && !rideTime.isAfter(hourEnd);
                })
                .count();
            
            BigDecimal revenue = transactionRepository.findAll().stream()
                .filter(this::isRiderFareDebit)
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> start == null && end == null || withinRange(t.getCreatedAt(), start, end))
                .filter(t -> {
                    LocalTime transTime = t.getCreatedAt().toLocalTime();
                    return !transTime.isBefore(hourStart) && !transTime.isAfter(hourEnd);
                })
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            data.add(DashboardStatsResponse.HourlyPerformanceData.builder()
                .hour(hour + "h")
                .rides(rides)
                .revenue(revenue)
                .build());
        }
        
        return data;
    }
    
    private List<DashboardStatsResponse.RecentActivityItem> generateRecentActivity(LocalDateTime start, LocalDateTime end) {
        // Get recent transactions and rides
        List<Transaction> recentTransactions = transactionRepository.findAll().stream()
            .filter(t -> start == null && end == null || withinRange(t.getCreatedAt(), start, end))
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

