package com.mssus.app.entity;

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
@EntityListeners(AuditingEntityListener.class)
public class Transactions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "txn_id")
    private Long txnId; // bigserial -> Long

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "direction")
    private String direction;

    @Column(name = "actor_kind")
    private String actorKind;

    @Column(name = "actor_user_id")
    private Integer actorUserId;

    @Column(name = "system_wallet")
    private String systemWallet;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "rider_user_id")
    private Integer riderUserId;

    @Column(name = "driver_user_id")
    private Integer driverUserId;

    @Column(name = "psp_ref")
    private String pspRef;

    @Column(name = "status")
    private String status;

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

