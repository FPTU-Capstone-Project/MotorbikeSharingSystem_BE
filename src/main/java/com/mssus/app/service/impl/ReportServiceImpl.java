package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.SystemWallet;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.dto.response.wallet.CommissionReportResponse;
import com.mssus.app.dto.response.wallet.DashboardResponse;
import com.mssus.app.dto.response.wallet.TopUpTrendResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.ReportService;
import com.mssus.app.common.exception.ValidationException;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculationService balanceCalculationService;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardStats(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now();
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        validateDateRange(effectiveStart, effectiveEnd);

        LocalDateTime startOfDay = effectiveStart.atStartOfDay();
        LocalDateTime endOfDay = effectiveEnd.atTime(LocalTime.MAX);

        long activeWallets = walletRepository.countByIsActiveTrue();
        
        // ✅ SSOT: Calculate total balance from ledger (not from Wallet entity)
        // Sum available and pending balances for all active wallets
        BigDecimal totalAvailable = BigDecimal.ZERO;
        BigDecimal totalPending = BigDecimal.ZERO;
        List<com.mssus.app.entity.Wallet> activeWalletsList = walletRepository.findAll().stream()
                .filter(w -> w.getIsActive() != null && w.getIsActive())
                .collect(java.util.stream.Collectors.toList());
        
        for (com.mssus.app.entity.Wallet wallet : activeWalletsList) {
            totalAvailable = totalAvailable.add(balanceCalculationService.calculateAvailableBalance(wallet.getWalletId()));
            totalPending = totalPending.add(balanceCalculationService.calculatePendingBalance(wallet.getWalletId()));
        }
        
        BigDecimal totalWalletBalance = totalAvailable.add(totalPending);

        BigDecimal topupsToday = defaultAmount(
                transactionRepository.sumAmountByTypeStatusDirectionAndActorBetween(
                        TransactionType.TOPUP, TransactionStatus.SUCCESS,
                        TransactionDirection.IN, ActorKind.USER, startOfDay, endOfDay));

        BigDecimal payoutsToday = defaultAmount(
                transactionRepository.sumAmountByTypeStatusDirectionAndActorBetween(
                        TransactionType.PAYOUT, TransactionStatus.SUCCESS,
                        TransactionDirection.OUT, ActorKind.USER, startOfDay, endOfDay));

        long pendingTransactions = transactionRepository.countByStatus(TransactionStatus.PENDING);
        long transactionsToday = transactionRepository.countTransactionsBetween(startOfDay, endOfDay);

        BigDecimal avgBalance = activeWallets > 0
                ? totalWalletBalance.divide(BigDecimal.valueOf(activeWallets), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal systemMasterBalance = defaultAmount(
                transactionRepository.netAmountBySystemWalletStatusAndDate(SystemWallet.MASTER, TransactionStatus.SUCCESS, startOfDay, endOfDay));
        BigDecimal systemCommissionBalance = defaultAmount(
                transactionRepository.netAmountBySystemWalletStatusAndDate(SystemWallet.COMMISSION, TransactionStatus.SUCCESS, startOfDay, endOfDay));
        BigDecimal liabilityCoverageGap = systemMasterBalance.subtract(totalWalletBalance);

        return DashboardResponse.builder()
                .totalActiveWallets(Math.toIntExact(activeWallets))
                .totalWalletBalance(totalWalletBalance)
                .totalTopupsToday(topupsToday)
                .totalPayoutsToday(payoutsToday)
                .pendingTransactionsCount(Math.toIntExact(pendingTransactions))
                .totalTransactionsToday(Math.toIntExact(transactionsToday))
                .avgWalletBalance(avgBalance)
                .totalCommissionCollected(systemCommissionBalance)
                .systemMasterBalance(systemMasterBalance)
                .systemCommissionBalance(systemCommissionBalance)
                .liabilityCoverageGap(liabilityCoverageGap)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TopUpTrendResponse getTopUpTrends(LocalDate startDate,
                                             LocalDate endDate,
                                             String interval,
                                             String paymentMethod) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(6);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        validateDateRange(effectiveStart, effectiveEnd);

        LocalDateTime startDateTime = effectiveStart.atStartOfDay();
        LocalDateTime endDateTime = effectiveEnd.atTime(LocalTime.MAX);

        List<Transaction> topups = transactionRepository.findByTypeAndStatusAndCreatedAtBetween(
                        TransactionType.TOPUP, TransactionStatus.SUCCESS, startDateTime, endDateTime)
                .stream()
                .filter(txn -> TransactionDirection.IN == txn.getDirection() && ActorKind.USER == txn.getActorKind())
                .collect(Collectors.toList());

        Map<LocalDate, List<Transaction>> grouped = topups.stream()
                .collect(Collectors.groupingBy(txn -> bucketDate(txn.getCreatedAt().toLocalDate(), interval)));

        List<TopUpTrendResponse.TrendDataPoint> points = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> TopUpTrendResponse.TrendDataPoint.builder()
                        .date(entry.getKey())
                        .amount(sumAmount(entry.getValue()))
                        .count(entry.getValue().size())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalAmount = sumAmount(topups);
        int totalCount = topups.size();
        BigDecimal avgAmount = totalCount > 0
                ? totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal previousPeriod = calculatePreviousPeriodTopups(effectiveStart, effectiveEnd);
        Double growthRate = computeGrowthRate(previousPeriod, totalAmount);

        return TopUpTrendResponse.builder()
                .dataPoints(points)
                .totalAmount(totalAmount)
                .avgTopUpAmount(avgAmount)
                .mostPopularPaymentMethod(determinePaymentMethod(topups, paymentMethod))
                .growthRate(growthRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CommissionReportResponse getCommissionReport(LocalDate startDate,
                                                        LocalDate endDate,
                                                        Integer driverId) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(6);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        validateDateRange(effectiveStart, effectiveEnd);

        LocalDateTime startDateTime = effectiveStart.atStartOfDay();
        LocalDateTime endDateTime = effectiveEnd.atTime(LocalTime.MAX);

        List<Transaction> captures = transactionRepository.findByTypeAndStatusAndCreatedAtBetween(
                TransactionType.CAPTURE_FARE, TransactionStatus.SUCCESS, startDateTime, endDateTime);

        Map<UUID, BigDecimal> commissionByGroup = new HashMap<>();
        Map<UUID, DriverEntry> driverByGroup = new HashMap<>();

        for (Transaction txn : captures) {
            UUID groupId = txn.getGroupId();
            if (groupId == null) {
                continue;
            }
            if (TransactionDirection.IN == txn.getDirection()
                    && ActorKind.SYSTEM == txn.getActorKind()
                    && SystemWallet.COMMISSION == txn.getSystemWallet()) {
                commissionByGroup.merge(groupId, defaultAmount(txn.getAmount()), BigDecimal::add);
            } else if (TransactionDirection.IN == txn.getDirection()
                    && ActorKind.USER == txn.getActorKind()
                    && txn.getActorUser() != null) {
                Integer currentDriverId = txn.getActorUser().getUserId();
                if (driverId != null && !driverId.equals(currentDriverId)) {
                    continue;
                }
                driverByGroup.put(groupId, new DriverEntry(currentDriverId, txn.getActorUser(), defaultAmount(txn.getAmount())));
            }
        }

        BigDecimal totalCommission;
        if (driverId != null) {
            totalCommission = driverByGroup.keySet().stream()
                    .map(groupId -> commissionByGroup.getOrDefault(groupId, BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            totalCommission = commissionByGroup.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        int totalBookings = (int) driverByGroup.keySet().size();
        BigDecimal avgCommission = totalBookings > 0
                ? totalCommission.divide(BigDecimal.valueOf(totalBookings), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<Integer, CommissionAccumulator> driverAggregates = new HashMap<>();
        for (Map.Entry<UUID, DriverEntry> entry : driverByGroup.entrySet()) {
            UUID groupId = entry.getKey();
            DriverEntry driverEntry = entry.getValue();
            BigDecimal driverCommission = commissionByGroup.getOrDefault(groupId, BigDecimal.ZERO);

            CommissionAccumulator accumulator = driverAggregates.computeIfAbsent(
                    driverEntry.driverId(),
                    id -> new CommissionAccumulator(driverEntry.driverId(), driverEntry.user()));

            accumulator.tripCount += 1;
            accumulator.totalEarnings = accumulator.totalEarnings.add(driverEntry.amount());
            accumulator.commissionPaid = accumulator.commissionPaid.add(driverCommission);
        }

        List<CommissionReportResponse.DriverCommission> driverCommissions = driverAggregates.values().stream()
                .map(acc -> CommissionReportResponse.DriverCommission.builder()
                        .driverId(acc.driverId)
                        .driverName(acc.user.getFullName())
                        .commissionPaid(acc.commissionPaid)
                        .tripCount(acc.tripCount)
                        .totalEarnings(acc.totalEarnings)
                        .build())
                .sorted(Comparator.comparing(CommissionReportResponse.DriverCommission::getCommissionPaid).reversed())
                .collect(Collectors.toList());

        return CommissionReportResponse.builder()
                .periodStart(effectiveStart)
                .periodEnd(effectiveEnd)
                .totalCommission(totalCommission)
                .totalBookings(totalBookings)
                .avgCommissionPerBooking(avgCommission)
                .driverCommissions(driverCommissions)
                .build();
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new ValidationException("Start date must be before end date");
        }
        LocalDate minAllowed = LocalDate.now().minusMonths(3);
        if (start.isBefore(minAllowed) || end.isBefore(minAllowed)) {
            throw new ValidationException("Date range cannot exceed 3 months back from today");
        }
        if (end.isAfter(LocalDate.now())) {
            throw new ValidationException("End date cannot be in the future");
        }
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDate bucketDate(LocalDate date, String interval) {
        if (interval == null || interval.isBlank() || interval.equalsIgnoreCase("daily")) {
            return date;
        }
        if (interval.equalsIgnoreCase("weekly")) {
            return date.minusDays(date.getDayOfWeek().getValue() - 1L);
        }
        if (interval.equalsIgnoreCase("monthly")) {
            return date.withDayOfMonth(1);
        }
        return date;
    }

    private BigDecimal sumAmount(List<Transaction> transactions) {
        return transactions.stream()
                .map(Transaction::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculatePreviousPeriodTopups(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(days - 1);
        return defaultAmount(transactionRepository.sumAmountByTypeStatusDirectionAndActorBetween(
                TransactionType.TOPUP,
                TransactionStatus.SUCCESS,
                TransactionDirection.IN,
                ActorKind.USER,
                prevStart.atStartOfDay(),
                prevEnd.atTime(LocalTime.MAX)
        ));
    }

    private Double computeGrowthRate(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal delta = current.subtract(previous);
        BigDecimal rate = delta.divide(previous, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        return rate.doubleValue();
    }

    private String determinePaymentMethod(List<Transaction> transactions, String preferredMethod) {
        if (preferredMethod != null && !preferredMethod.isBlank()) {
            return preferredMethod;
        }
        long withPspRef = transactions.stream()
                .filter(txn -> txn.getPspRef() != null && !txn.getPspRef().isBlank())
                .count();
        if (withPspRef == 0) {
            return "UNKNOWN";
        }
        return "PSP";
    }

    private static class DriverEntry {
        private final Integer driverId;
        private final User user;
        private final BigDecimal amount;

        DriverEntry(Integer driverId, User user, BigDecimal amount) {
            this.driverId = driverId;
            this.user = user;
            this.amount = amount;
        }

        Integer driverId() {
            return driverId;
        }

        User user() {
            return user;
        }

        BigDecimal amount() {
            return amount;
        }
    }

    private static class CommissionAccumulator {
        private final Integer driverId;
        private final User user;
        private BigDecimal commissionPaid = BigDecimal.ZERO;
        private BigDecimal totalEarnings = BigDecimal.ZERO;
        private int tripCount = 0;

        CommissionAccumulator(Integer driverId, User user) {
            this.driverId = driverId;
            this.user = user;
        }
    }
}
