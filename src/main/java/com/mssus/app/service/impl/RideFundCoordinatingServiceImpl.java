package com.mssus.app.service.impl;

import com.amazonaws.services.sns.model.NotFoundException;
import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.CreateTransactionRequest;
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
import com.mssus.app.service.NotificationService;
import com.mssus.app.service.RideFundCoordinatingService;
import com.mssus.app.service.TransactionService;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideFundCoordinatingServiceImpl implements RideFundCoordinatingService {
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final NotificationService notificationService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    private final static String CURRENCY = "VND";

    @Override
    @Transactional
    public void holdRideFunds(RideConfirmHoldRequest request) {
        Integer userId = request.getRiderId();
        BigDecimal amount = request.getAmount();

        Wallet walletBefore = walletService.getWalletByUserId(userId);
        if (walletBefore.getShadowBalance().compareTo(amount) < 0) {
            throw new ValidationException("Insufficient balance for hold. Available: " + walletBefore.getShadowBalance());
        }

        walletService.decreaseShadowBalance(userId, amount);
        walletService.increasePendingBalance(userId, amount);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));

        BigDecimal afterAvail = walletBefore.getShadowBalance().subtract(amount);
        BigDecimal afterPending = walletBefore.getPendingBalance().add(amount);

        CreateTransactionRequest txnRequest = new CreateTransactionRequest(
            UUID.randomUUID(), TransactionType.HOLD_CREATE, TransactionDirection.INTERNAL,
            ActorKind.USER, userId, null, amount, CURRENCY,
            null, request.getRideRequestId(), null, TransactionStatus.SUCCESS,
            request.getNote(), walletBefore.getShadowBalance(), afterAvail,
            walletBefore.getPendingBalance(), afterPending
        );

        log.info("Holding funds for user {}, amount {}", userId, amount);

        transactionService.createTransaction(txnRequest);

        String riderPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            txnRequest.beforeAvail(),
            txnRequest.afterAvail(),
            txnRequest.amount(),
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
        //2. Decrease pending balance from rider
        //3. Increase available balance to driver
        //4. Create 3 transactions row with same groupId
        UUID groupId = UUID.randomUUID();

        SettlementResult settlementResult = pricingService.settle(fareBreakdown);
        Wallet riderWalletBefore = walletService.getWalletByUserId(request.getRiderId());
        Wallet driverWalletBefore = walletService.getWalletByUserId(request.getDriverId());

        log.info("Settling ride funds for rider {}, driver {}, amount {}", request.getRiderId(),
            request.getDriverId(), settlementResult.riderPay().amount());

        // Manually calculate post-transaction balances for accurate logging
        BigDecimal riderAfterAvail = riderWalletBefore.getShadowBalance();
        BigDecimal riderAfterPending = riderWalletBefore.getPendingBalance().subtract(settlementResult.riderPay().amount());
        BigDecimal driverAfterAvail = driverWalletBefore.getShadowBalance().add(settlementResult.driverPayout().amount());
        BigDecimal driverAfterPending = driverWalletBefore.getPendingBalance();

        CreateTransactionRequest riderPayTxnRequest = new CreateTransactionRequest(
            groupId, TransactionType.CAPTURE_FARE, TransactionDirection.OUT,
            ActorKind.USER, request.getRiderId(), null,
            settlementResult.riderPay().amount(), CURRENCY,
            request.getRideId(), request.getRideRequestId(), null, TransactionStatus.SUCCESS,
            "Rider payment for ride " + request.getRideRequestId(),
            riderWalletBefore.getShadowBalance(), riderAfterAvail,
            riderWalletBefore.getPendingBalance(), riderAfterPending
        );

        CreateTransactionRequest driverPayoutTxnRequest = new CreateTransactionRequest(
            groupId, TransactionType.CAPTURE_FARE, TransactionDirection.IN,
            ActorKind.USER, request.getDriverId(), null,
            settlementResult.driverPayout().amount(), CURRENCY,
            request.getRideId(), request.getRideRequestId(), null, TransactionStatus.SUCCESS,
            "Driver payout for ride " + request.getRideRequestId(),
            driverWalletBefore.getShadowBalance(), driverAfterAvail,
            driverWalletBefore.getPendingBalance(), driverAfterPending
        );

        CreateTransactionRequest commissionTxnRequest = new CreateTransactionRequest(
            groupId, TransactionType.CAPTURE_FARE, TransactionDirection.IN,
            ActorKind.SYSTEM, null, SystemWallet.COMMISSION,
            settlementResult.commission().amount(), CURRENCY,
            request.getRideId(), request.getRideRequestId(), null, TransactionStatus.SUCCESS,
            "Platform commission for ride " + request.getRideRequestId(),
            BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO
        );

        walletService.decreasePendingBalance(request.getRiderId(), settlementResult.riderPay().amount());
        walletService.increaseShadowBalance(request.getDriverId(), settlementResult.driverPayout().amount());

        transactionService.createTransaction(riderPayTxnRequest);
        transactionService.createTransaction(driverPayoutTxnRequest);
        transactionService.createTransaction(commissionTxnRequest);

        String riderPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            riderPayTxnRequest.beforeAvail(),
            riderPayTxnRequest.afterAvail(),
            riderPayTxnRequest.amount(),
            request.getRideRequestId()
        );

        User rider = userRepository.findById(request.getRiderId()).orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));
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
            driverPayoutTxnRequest.beforeAvail(),
            driverPayoutTxnRequest.afterAvail(),
            driverPayoutTxnRequest.amount(),
            request.getRideRequestId()
        );

        User driver = userRepository.findById(request.getDriverId()).orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));
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
        Transaction transaction = transactionRepository.findAll().stream()
            .filter(t -> t.getSharedRideRequest() != null && t.getSharedRideRequest().getSharedRideRequestId().equals(request.getRideRequestId()))
            .filter(t -> t.getActorUser() != null && t.getActorUser().getUserId().equals(request.getRiderId()))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Transaction not found for request: " + request.getRiderId()));

        if (transaction.getGroupId() == null) {
            throw new NotFoundException("Group ID not found for bookingId: " + request.getRiderId());
        }
        UUID groupId = transaction.getGroupId();

        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
        Transaction holdTransaction = holdTransactions.stream()
            .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));

        boolean alreadyReleased = holdTransactions.stream()
            .anyMatch(t -> t.getType() == TransactionType.HOLD_RELEASE);
        if (alreadyReleased) {
            throw new ValidationException("Hold has already been released for groupId: " + groupId);
        }

        BigDecimal heldAmount = holdTransaction.getAmount();
        Integer riderId = request.getRiderId();

        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
            .orElseThrow(() -> new NotFoundException("Rider wallet not found"));

        UUID newGroupId = UUID.randomUUID();

        BigDecimal afterAvail = riderWallet.getShadowBalance().add(heldAmount);
        BigDecimal afterPending = riderWallet.getPendingBalance().subtract(heldAmount);

        CreateTransactionRequest releaseTxnRequest = new CreateTransactionRequest(
            newGroupId, TransactionType.HOLD_RELEASE, TransactionDirection.INTERNAL,
            ActorKind.USER, request.getRiderId(), null, heldAmount, CURRENCY,
            null, request.getRideRequestId(), null, TransactionStatus.SUCCESS,
            request.getNote(), riderWallet.getShadowBalance(), afterAvail,
            riderWallet.getPendingBalance(), afterPending
        );

        // Persist balance changes
        walletService.increaseShadowBalance(riderId, heldAmount);
        walletService.decreasePendingBalance(riderId, heldAmount);
        log.info("Releasing held funds for rider {}, amount {}", riderId, heldAmount);

        transactionService.createTransaction(releaseTxnRequest);

        String riderPayloadJson = String.format(
            "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
            releaseTxnRequest.beforeAvail(),
            releaseTxnRequest.afterAvail(),
            releaseTxnRequest.amount(),
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
