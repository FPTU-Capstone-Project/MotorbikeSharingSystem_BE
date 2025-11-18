package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ✅ FIX P0-BALANCE_CACHE: Entity để lưu balance snapshots hàng ngày
 * 
 * Mục đích:
 * - Audit trail: Lưu lịch sử balance mỗi ngày
 * - Balance reconciliation: So sánh snapshot với calculated balance
 * - Compliance: Đáp ứng yêu cầu lưu trữ dữ liệu tài chính
 * - Trend analysis: Phân tích xu hướng balance theo thời gian
 */
@Entity
@Table(name = "wallet_balance_snapshots", indexes = {
    @Index(name = "idx_snapshot_wallet_date", columnList = "wallet_id,snapshot_date"),
    @Index(name = "idx_snapshot_date", columnList = "snapshot_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class WalletBalanceSnapshot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;
    
    @Column(name = "available_balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal availableBalance;
    
    @Column(name = "pending_balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal pendingBalance;
    
    @Column(name = "total_balance", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalBalance;
    
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;
    
    @Column(name = "transaction_count")
    private long transactionCount;  // Số lượng transactions tính đến snapshot date
    
    @Column(name = "notes", length = 500)
    private String notes;  // Ghi chú (e.g., "Daily snapshot", "Reconciliation snapshot")
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

