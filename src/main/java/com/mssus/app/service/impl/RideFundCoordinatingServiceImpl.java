package com.mssus.app.service.impl;

import com.amazonaws.services.sns.model.NotFoundException;
import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.ride.RideRequestSettledResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.RideFundCoordinatingService;
import com.mssus.app.service.WalletService;
import com.mssus.app.service.domain.pricing.PricingService;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.SettlementResult;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideFundCoordinatingServiceImpl implements RideFundCoordinatingService {
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final NotificationService notificationService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BalanceCalculationService balanceCalculationService;  // ✅ SSOT: Calculate balance from ledger

    private final static String CURRENCY = "VND";

    @Override
    @Transactional
    public void holdRideFunds(RideConfirmHoldRequest request) {
        Integer userId = request.getRiderId();
        BigDecimal amount = request.getAmount();

        Wallet wallet = walletService.getWalletByUserId(userId);
        
        // ✅ SSOT: Check balance từ ledger
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(wallet.getWalletId());
        BigDecimal pendingBalance = balanceCalculationService.calculatePendingBalance(wallet.getWalletId());
        
        if (availableBalance.compareTo(amount) < 0) {
            throw new ValidationException("Insufficient balance for hold. Available: " + availableBalance);
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));

        // ✅ SSOT: Sử dụng WalletService.holdAmount() method
        UUID groupId = UUID.randomUUID();
        Transaction holdTxn = walletService.holdAmount(
            wallet.getWalletId(),
            amount,
            groupId,
            request.getNote() != null ? request.getNote() : "Hold for ride request #" + request.getRideRequestId()
        );

        // Calculate snapshots for audit trail
        BigDecimal afterAvail = availableBalance.subtract(amount);

        log.info("Holding funds for user {}, amount {}, groupId {}", userId, amount, groupId);

        // ✅ SSOT: Transaction đã được tạo bởi WalletService.holdAmount()
        // KHÔNG cần tạo transaction thêm nữa

        String riderPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            availableBalance,
            afterAvail,
            amount,
            request.getRideRequestId()
        );

        notificationService.sendNotification(user,
            NotificationType.WALLET_HOLD,
            "Funds on Hold",
            String.format("%,.2f has been held for booking request #%d", request.getAmount(), request.getRideRequestId()),
            null,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            null);
    }

    @Override
    @Transactional
    public RideRequestSettledResponse settleRideFunds(RideCompleteSettlementRequest request, FareBreakdown fareBreakdown) {
        //1. Call Pricing Service to get settlement result
        //2. ✅ SSOT: Tạo CAPTURE_FARE transactions (không update balance trực tiếp)
        //3. Create 3 transactions row with same groupId
        UUID groupId = UUID.randomUUID();

        SettlementResult settlementResult = pricingService.settle(fareBreakdown);
        Wallet riderWallet = walletService.getWalletByUserId(request.getRiderId());
        Wallet driverWallet = walletService.getWalletByUserId(request.getDriverId());

        log.info("Settling ride funds for rider {}, driver {}, amount {}", request.getRiderId(),
            request.getDriverId(), settlementResult.riderPay().amount());

        // ✅ SSOT: Get balances từ ledger for snapshots
        BigDecimal riderAvailableBefore = balanceCalculationService.calculateAvailableBalance(riderWallet.getWalletId());
        BigDecimal riderPendingBefore = balanceCalculationService.calculatePendingBalance(riderWallet.getWalletId());
        BigDecimal driverAvailableBefore = balanceCalculationService.calculateAvailableBalance(driverWallet.getWalletId());
        BigDecimal driverPendingBefore = balanceCalculationService.calculatePendingBalance(driverWallet.getWalletId());

        // Calculate snapshots for audit trail
        BigDecimal riderAfterAvail = riderAvailableBefore;
        BigDecimal riderAfterPending = riderPendingBefore.subtract(settlementResult.riderPay().amount());
        BigDecimal driverAfterAvail = driverAvailableBefore.add(settlementResult.driverPayout().amount());
        BigDecimal driverAfterPending = driverPendingBefore;

        User rider = userRepository.findById(request.getRiderId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));
        User driver = userRepository.findById(request.getDriverId())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));

        // ✅ SSOT: Create rider payment transaction (OUT)
        Transaction riderPayTxn = Transaction.builder()
            .groupId(groupId)
            .wallet(riderWallet)  // ✅ Set wallet relationship
            .type(TransactionType.CAPTURE_FARE)
            .direction(TransactionDirection.OUT)
            .actorKind(ActorKind.USER)
            .actorUser(rider)
            .amount(settlementResult.riderPay().amount())
            .currency(CURRENCY)
            .sharedRide(null)  // TODO: Set sharedRide relationship if needed
            .status(TransactionStatus.SUCCESS)
            .beforeAvail(riderAvailableBefore)  // Snapshot for audit
            .afterAvail(riderAfterAvail)  // Snapshot for audit
            .beforePending(riderPendingBefore)  // Snapshot for audit
            .afterPending(riderAfterPending)  // Snapshot for audit
            .note("Rider payment for ride " + request.getRideRequestId())
            .build();

        // ✅ SSOT: Create driver payout transaction (IN)
        Transaction driverPayoutTxn = Transaction.builder()
            .groupId(groupId)
            .wallet(driverWallet)  // ✅ Set wallet relationship
            .type(TransactionType.CAPTURE_FARE)
            .direction(TransactionDirection.IN)
            .actorKind(ActorKind.USER)
            .actorUser(driver)
            .amount(settlementResult.driverPayout().amount())
            .currency(CURRENCY)
            .sharedRide(null)  // TODO: Set sharedRide relationship if needed
            .status(TransactionStatus.SUCCESS)
            .beforeAvail(driverAvailableBefore)  // Snapshot for audit
            .afterAvail(driverAfterAvail)  // Snapshot for audit
            .beforePending(driverPendingBefore)  // Snapshot for audit
            .afterPending(driverAfterPending)  // Snapshot for audit
            .note("Driver payout for ride " + request.getRideRequestId())
            .build();

        // ✅ SSOT: Create commission transaction (system)
        Transaction commissionTxn = Transaction.builder()
            .groupId(groupId)
            .type(TransactionType.CAPTURE_FARE)
            .direction(TransactionDirection.IN)
            .actorKind(ActorKind.SYSTEM)
            .systemWallet(SystemWallet.COMMISSION)
            .amount(settlementResult.commission().amount())
            .currency(CURRENCY)
            .sharedRide(null)  // TODO: Set sharedRide relationship if needed
            .status(TransactionStatus.SUCCESS)
            .note("Platform commission for ride " + request.getRideRequestId())
            .build();

        transactionRepository.save(riderPayTxn);
        transactionRepository.save(driverPayoutTxn);
        transactionRepository.save(commissionTxn);

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query

        String riderPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            riderAvailableBefore,
            riderAfterAvail,
            settlementResult.riderPay().amount(),
            request.getRideRequestId()
        );

        notificationService.sendNotification(rider,
            NotificationType.WALLET_CAPTURE,
            "Payment Successful",
            String.format("%,.0f VND has been paid for your ride.", settlementResult.riderPay().amount()),
            riderPayloadJson,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            null);

        String driverPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            driverAvailableBefore,
            driverAfterAvail,
            settlementResult.driverPayout().amount(),
            request.getRideRequestId()
        );

        notificationService.sendNotification(driver,
            NotificationType.WALLET_PAYOUT,
            "You've Been Paid",
            String.format("You received %,.0f VND for completing a ride.", settlementResult.driverPayout().amount()),
            driverPayloadJson,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            null);

        return new RideRequestSettledResponse(
            settlementResult.driverPayout().amount(),
            settlementResult.commission().amount()
        );
    }

    @Override
    @Transactional
    public void releaseRideFunds(RideHoldReleaseRequest request) {
        // Find the original hold transaction
        Transaction transaction = transactionRepository.findAll().stream()
            .filter(t -> t.getSharedRideRequest() != null && t.getSharedRideRequest().getSharedRideRequestId().equals(request.getRideRequestId()))
            .filter(t -> t.getActorUser() != null && t.getActorUser().getUserId().equals(request.getRiderId()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Transaction not found for request: " + request.getRiderId()));

        if (transaction.getGroupId() == null) {
            throw new NotFoundException("Group ID not found for bookingId: " + request.getRiderId());
        }
        UUID groupId = transaction.getGroupId();

        // ✅ SSOT: Sử dụng WalletService.releaseHold() method
        Transaction releaseTxn = walletService.releaseHold(
            groupId,
            request.getNote() != null ? request.getNote() : "Release hold for ride request #" + request.getRideRequestId()
        );

        BigDecimal heldAmount = releaseTxn.getAmount();
        Integer riderId = request.getRiderId();

        // ✅ SSOT: Get balances từ ledger for notification payload
        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
            .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
        
        BigDecimal availableBalance = balanceCalculationService.calculateAvailableBalance(riderWallet.getWalletId());

        log.info("Releasing held funds for rider {}, amount {}, groupId {}", riderId, heldAmount, groupId);

        // ✅ SSOT: Transaction đã được tạo bởi WalletService.releaseHold()
        // KHÔNG cần tạo transaction thêm nữa

        String riderPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            availableBalance.subtract(heldAmount),  // Before release
            availableBalance,  // After release
            heldAmount,
            request.getRideRequestId()
        );

        User rider = userRepository.findById(riderId).orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));
        notificationService.sendNotification(rider,
            NotificationType.WALLET_RELEASE,
            "Hold Released",
            String.format("The hold of %,.2f VND for booking request #%d has been released.", heldAmount, request.getRideRequestId()),
            riderPayloadJson,
            Priority.MEDIUM,
            DeliveryMethod.IN_APP,
            null);
    }
}
