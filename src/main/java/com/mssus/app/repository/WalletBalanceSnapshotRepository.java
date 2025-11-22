package com.mssus.app.repository;

import com.mssus.app.entity.Wallet;
import com.mssus.app.entity.WalletBalanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ✅ FIX P0-BALANCE_CACHE: Repository cho wallet balance snapshots
 */
@Repository
public interface WalletBalanceSnapshotRepository extends JpaRepository<WalletBalanceSnapshot, Long> {
    
    /**
     * Tìm snapshot mới nhất của một wallet
     */
    Optional<WalletBalanceSnapshot> findFirstByWalletOrderBySnapshotDateDesc(Wallet wallet);
    
    /**
     * Tìm snapshot theo wallet và date
     */
    Optional<WalletBalanceSnapshot> findByWalletAndSnapshotDate(Wallet wallet, LocalDate date);
    
    /**
     * Tìm tất cả snapshots của một wallet, sắp xếp theo date DESC
     */
    List<WalletBalanceSnapshot> findByWalletOrderBySnapshotDateDesc(Wallet wallet);
    
    /**
     * Tìm tất cả snapshots trong khoảng thời gian
     */
    @Query("SELECT s FROM WalletBalanceSnapshot s WHERE s.snapshotDate BETWEEN :startDate AND :endDate ORDER BY s.snapshotDate DESC")
    List<WalletBalanceSnapshot> findBySnapshotDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
    
    /**
     * Tìm snapshot theo date (tất cả wallets)
     */
    List<WalletBalanceSnapshot> findBySnapshotDate(LocalDate date);
    
    /**
     * Kiểm tra xem đã có snapshot cho wallet và date chưa
     */
    boolean existsByWalletAndSnapshotDate(Wallet wallet, LocalDate date);
    
    /**
     * Xóa snapshots cũ hơn một số ngày (cleanup)
     */
    @Query("DELETE FROM WalletBalanceSnapshot s WHERE s.snapshotDate < :thresholdDate")
    void deleteBySnapshotDateBefore(@Param("thresholdDate") LocalDate thresholdDate);
}

