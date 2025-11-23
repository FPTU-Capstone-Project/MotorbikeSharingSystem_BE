package com.mssus.app.scheduler;

import com.mssus.app.entity.Wallet;
import com.mssus.app.entity.WalletBalanceSnapshot;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.WalletBalanceSnapshotRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ✅ FIX P0-BALANCE_CACHE: Service để tạo và quản lý balance snapshots
 * 
 * Mục đích:
 * - Audit trail: Lưu lịch sử balance mỗi ngày
 * - Balance reconciliation: So sánh snapshot với calculated balance
 * - Compliance: Đáp ứng yêu cầu lưu trữ dữ liệu tài chính
 * - Trend analysis: Phân tích xu hướng balance theo thời gian
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BalanceSnapshotScheduler {
    private final WalletRepository walletRepository;
    private final WalletBalanceSnapshotRepository snapshotRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculationService balanceCalculationService;
    
    /**
     * ✅ FIX P0-BALANCE_CACHE: Tạo balance snapshot mỗi ngày
     * Runs daily at 1 AM
     */
    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    @Transactional
    public void createDailyBalanceSnapshots() {
        log.info("Starting daily balance snapshot creation");
        
        LocalDate today = LocalDate.now();
        List<Wallet> wallets = walletRepository.findAll();
        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        
        for (Wallet wallet : wallets) {
            try {
                // Kiểm tra xem đã có snapshot cho hôm nay chưa
                if (snapshotRepository.existsByWalletAndSnapshotDate(wallet, today)) {
                    log.debug("Snapshot already exists for wallet {} on date {}, skipping", 
                        wallet.getWalletId(), today);
                    skippedCount++;
                    continue;
                }
                
                // Tính balance từ ledger
                BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
                BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());
                BigDecimal totalBalance = availableBalance.add(pendingBalance);
                
                // Đếm số lượng transactions (SUCCESS) của wallet
                Long transactionCount = transactionRepository.countByWallet_WalletIdAndStatus(
                    wallet.getWalletId(), 
                    com.mssus.app.common.enums.TransactionStatus.SUCCESS);
                
                // Tạo và lưu snapshot
                WalletBalanceSnapshot snapshot = WalletBalanceSnapshot.builder()
                    .wallet(wallet)
                    .availableBalance(availableBalance)
                    .pendingBalance(pendingBalance)
                    .totalBalance(totalBalance)
                    .snapshotDate(today)
                    .transactionCount(transactionCount)
                    .notes("Daily snapshot - Automated")
                    .build();
                
                snapshotRepository.save(snapshot);
                
                log.debug("Balance snapshot created for wallet {}: available={}, pending={}, total={}, transactions={}", 
                    wallet.getWalletId(), availableBalance, pendingBalance, totalBalance, transactionCount);
                
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to create balance snapshot for wallet {}: {}", 
                    wallet.getWalletId(), e.getMessage(), e);
            }
        }
        
        log.info("Daily balance snapshot completed: total={}, success={}, skipped={}, errors={}", 
            wallets.size(), successCount, skippedCount, errorCount);
    }
    
    /**
     * Tạo snapshot cho một wallet cụ thể (manual)
     */
    @Transactional
    public WalletBalanceSnapshot createSnapshotForWallet(Integer walletId, LocalDate date, String notes) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new com.mssus.app.common.exception.NotFoundException("Wallet not found: " + walletId));
        
        // Kiểm tra xem đã có snapshot chưa
        if (snapshotRepository.existsByWalletAndSnapshotDate(wallet, date)) {
            log.warn("Snapshot already exists for wallet {} on date {}", walletId, date);
            return snapshotRepository.findByWalletAndSnapshotDate(wallet, date)
                .orElseThrow(() -> new RuntimeException("Snapshot exists but not found"));
        }
        
        // Tính balance từ ledger
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(walletId);
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(walletId);
        BigDecimal totalBalance = availableBalance.add(pendingBalance);
        
        // Đếm số lượng transactions
        long transactionCount = transactionRepository.countByWallet_WalletIdAndStatus(
            walletId, 
            com.mssus.app.common.enums.TransactionStatus.SUCCESS);
        
        // Tạo và lưu snapshot
        WalletBalanceSnapshot snapshot = WalletBalanceSnapshot.builder()
            .wallet(wallet)
            .availableBalance(availableBalance)
            .pendingBalance(pendingBalance)
            .totalBalance(totalBalance)
            .snapshotDate(date)
            .transactionCount(transactionCount)
            .notes(notes != null ? notes : "Manual snapshot")
            .build();
        
        return snapshotRepository.save(snapshot);
    }
    
    /**
     * Lấy snapshot mới nhất của một wallet
     */
    public Optional<WalletBalanceSnapshot> getLatestSnapshot(Integer walletId) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new com.mssus.app.common.exception.NotFoundException("Wallet not found: " + walletId));
        return snapshotRepository.findFirstByWalletOrderBySnapshotDateDesc(wallet);
    }
    
    /**
     * So sánh snapshot với calculated balance (reconciliation)
     * Trả về true nếu khớp, false nếu có discrepancy
     */
    public boolean reconcileSnapshot(Integer walletId) {
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new com.mssus.app.common.exception.NotFoundException("Wallet not found: " + walletId));
        
        Optional<WalletBalanceSnapshot> latestSnapshot = snapshotRepository
            .findFirstByWalletOrderBySnapshotDateDesc(wallet);
        
        if (latestSnapshot.isEmpty()) {
            log.warn("No snapshot found for wallet {}, cannot reconcile", walletId);
            return false;
        }
        
        WalletBalanceSnapshot snapshot = latestSnapshot.get();
        
        // Tính balance hiện tại từ ledger
        BigDecimal currentAvailable = balanceCalculationService.calculateAvailableBalance(walletId);
        BigDecimal currentPending = balanceCalculationService.calculatePendingBalance(walletId);
        BigDecimal currentTotal = currentAvailable.add(currentPending);
        
        // So sánh (cho phép sai số nhỏ do rounding)
        BigDecimal tolerance = BigDecimal.valueOf(0.01); // 0.01 VND tolerance
        
        boolean availableMatches = snapshot.getAvailableBalance()
            .subtract(currentAvailable).abs().compareTo(tolerance) <= 0;
        boolean pendingMatches = snapshot.getPendingBalance()
            .subtract(currentPending).abs().compareTo(tolerance) <= 0;
        boolean totalMatches = snapshot.getTotalBalance()
            .subtract(currentTotal).abs().compareTo(tolerance) <= 0;
        
        if (!availableMatches || !pendingMatches || !totalMatches) {
            log.warn("Balance discrepancy detected for wallet {}: " +
                "snapshot available={}, pending={}, total={} vs " +
                "calculated available={}, pending={}, total={}",
                walletId,
                snapshot.getAvailableBalance(), snapshot.getPendingBalance(), snapshot.getTotalBalance(),
                currentAvailable, currentPending, currentTotal);
            return false;
        }
        
        log.info("Balance reconciliation passed for wallet {}: all balances match", walletId);
        return true;
    }
    
    /**
     * Cleanup snapshots cũ hơn một số ngày (e.g., 365 ngày)
     * Runs monthly at 2 AM on the 1st day
     */
    @Scheduled(cron = "0 0 2 1 * *") // Monthly at 2 AM on the 1st day
    @Transactional
    public void cleanupOldSnapshots() {
        log.info("Starting old snapshot cleanup");
        
        LocalDate thresholdDate = LocalDate.now().minusDays(365); // Giữ 1 năm
        
        snapshotRepository.deleteBySnapshotDateBefore(thresholdDate);
        
        log.info("Old snapshot cleanup completed: deleted snapshots before {}", thresholdDate);
    }
}

