package com.mssus.app.entity;

import com.mssus.app.common.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table(name = "transactions")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "txn_id")
    private Integer txnId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(name = "direction", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionDirection direction;

    @Column(name = "actor_kind", nullable = false)
    @Enumerated(EnumType.STRING)
    private ActorKind actorKind;

    @ManyToOne
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(name = "system_wallet")
    @Enumerated(EnumType.STRING)
    private SystemWallet systemWallet;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "VND";

    @ManyToOne
    @JoinColumn(name = "shared_ride_id")
    private SharedRide sharedRide;

    @ManyToOne
    @JoinColumn(name = "shared_ride_request_id")
    private SharedRideRequest sharedRideRequest;

    @Column(name = "psp_ref")
    private String pspRef;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "before_avail", precision = 18, scale = 2)
    private BigDecimal beforeAvail;

    @Column(name = "after_avail", precision = 18, scale = 2)
    private BigDecimal afterAvail;

    @Column(name = "before_pending", precision = 18, scale = 2)
    private BigDecimal beforePending;

    @Column(name = "after_pending", precision = 18, scale = 2)
    private BigDecimal afterPending;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "note")
    private String note;
}