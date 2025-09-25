package com.mssus.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wallet_id")
    private Integer walletId;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", unique = true)
    private Users user;

    @Column(name = "psp_account_id")
    private String pspAccountId;

    @Column(name = "shadow_balance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shadowBalance = BigDecimal.ZERO;

    @Column(name = "pending_balance", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(name = "total_topped_up", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalToppedUp = BigDecimal.ZERO;

    @Column(name = "total_spent", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
