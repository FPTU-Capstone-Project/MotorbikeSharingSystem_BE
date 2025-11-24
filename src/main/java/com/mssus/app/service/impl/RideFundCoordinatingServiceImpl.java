package com.mssus.app.service.impl;

import com.amazonaws.services.sns.model.NotFoundException;
import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.wallet.RideCompleteSettlementRequest;
import com.mssus.app.dto.request.wallet.RideConfirmHoldRequest;
import com.mssus.app.dto.request.wallet.RideHoldReleaseRequest;
import com.mssus.app.dto.response.ride.RideRequestSettledResponse;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.SharedRideRepository;
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
import java.util.List;
import java.util.Optional;
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
    private final SharedRideRepository sharedRideRepository;
    private final BalanceCalculationService balanceCalculationService; // ✅ SSOT: Calculate balance from ledger

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
        walletService.holdAmount(
                wallet.getWalletId(),
                amount,
                groupId,
                request.getNote() != null ? request.getNote() : "Hold for ride request #" + request.getRideRequestId(),
                request.getRideRequestId() // ✅ FIX: Pass sharedRideRequestId for database constraint
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
                request.getRideRequestId());

        notificationService.sendNotification(user,
                NotificationType.WALLET_HOLD,
                "Funds on Hold",
                String.format("%,.2f has been held for booking request #%d", request.getAmount(),
                        request.getRideRequestId()),
                riderPayloadJson,
                Priority.MEDIUM,
                DeliveryMethod.IN_APP,
                null);
    }

    @Override
    @Transactional
    public RideRequestSettledResponse settleRideFunds(RideCompleteSettlementRequest request,
            FareBreakdown fareBreakdown) {
        // 1. Call Pricing Service to get settlement result
        // 2. ✅ SSOT: Tạo CAPTURE_FARE transactions (không update balance trực tiếp)
        // 3. Create 3 transactions row with same groupId
        UUID groupId = UUID.randomUUID();

        SettlementResult settlementResult = pricingService.settle(fareBreakdown);
        Wallet riderWallet = walletService.getWalletByUserId(request.getRiderId());
        Wallet driverWallet = walletService.getWalletByUserId(request.getDriverId());

        // ✅ FIX: Load SharedRide entity (required for database constraint
        // txn_booking_required_for_ride)
        // Constraint requires: CAPTURE_FARE must have shared_ride_id IS NOT NULL
        if (request.getRideId() == null) {
            throw new ValidationException("Ride ID is required for CAPTURE_FARE transaction");
        }
        SharedRide sharedRide = sharedRideRepository.findById(request.getRideId())
                .orElseThrow(() -> BaseDomainException.of("ride.not-found"));

        // ✅ FIX P0-CONCURRENCY: Lock wallets để tránh race condition
        Wallet lockedRiderWallet = walletRepository.findByIdWithLock(riderWallet.getWalletId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.wallet"));
        Wallet lockedDriverWallet = walletRepository.findByIdWithLock(driverWallet.getWalletId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.wallet"));

        // ✅ FIX P0-CAPTURE_FARE: Validate triple-entry balancing
        // rider payment MUST equal driver payout + commission
        BigDecimal riderPayAmount = settlementResult.riderPay().amount();
        BigDecimal driverPayoutAmount = settlementResult.driverPayout().amount();
        BigDecimal commissionAmount = settlementResult.commission().amount();
        BigDecimal totalOut = driverPayoutAmount.add(commissionAmount);

        if (riderPayAmount.compareTo(totalOut) != 0) {
            String errorMsg = String.format(
                    "CAPTURE_FARE triple-entry balancing violation: riderPay (%s) != driverPayout (%s) + commission (%s) = %s",
                    riderPayAmount, driverPayoutAmount, commissionAmount, totalOut);
            log.error(errorMsg);
            throw new ValidationException(errorMsg);
        }

        log.info("Settling ride funds for rider {}, driver {}, riderPay: {}, driverPayout: {}, commission: {}",
                request.getRiderId(), request.getDriverId(),
                riderPayAmount, driverPayoutAmount, commissionAmount);

        // ✅ SSOT: Get balances từ ledger for snapshots (sau khi lock)
        BigDecimal riderAvailableBefore = balanceCalculationService
                .calculateAvailableBalance(lockedRiderWallet.getWalletId());
        BigDecimal riderPendingBefore = balanceCalculationService
                .calculatePendingBalance(lockedRiderWallet.getWalletId());
        BigDecimal driverAvailableBefore = balanceCalculationService
                .calculateAvailableBalance(lockedDriverWallet.getWalletId());
        BigDecimal driverPendingBefore = balanceCalculationService
                .calculatePendingBalance(lockedDriverWallet.getWalletId());

        // Calculate snapshots for audit trail
        BigDecimal riderAfterAvail = riderAvailableBefore;
        BigDecimal riderAfterPending = riderPendingBefore.subtract(settlementResult.riderPay().amount());
        BigDecimal driverAfterAvail = driverAvailableBefore.add(settlementResult.driverPayout().amount());
        BigDecimal driverAfterPending = driverPendingBefore;

        User rider = userRepository.findById(request.getRiderId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));
        User driver = userRepository.findById(request.getDriverId())
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));

        // ✅ FIX P0-CAPTURE_IDEMPOTENCY: Check idempotency để tránh duplicate capture
        String idempotencyKey = "CAPTURE_FARE_" + request.getRideRequestId() + "_" + groupId.toString();
        Optional<Transaction> existingCapture = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingCapture.isPresent()) {
            log.warn("Ride already captured for requestId: {}, groupId: {}", request.getRideRequestId(), groupId);
            // Return existing transactions
            List<Transaction> existingTransactions = transactionRepository
                    .findByGroupId(existingCapture.get().getGroupId());
            // Return existing settlement response
            // TODO: Build response from existing transactions
            throw new ValidationException("Ride already captured");
        }

        // ✅ SSOT: Create rider payment transaction (OUT)
        Transaction riderPayTxn = Transaction.builder()
                .groupId(groupId)
                .wallet(lockedRiderWallet) // ✅ Set wallet relationship (use locked wallet)
                .idempotencyKey(idempotencyKey) // ✅ FIX P0-CAPTURE_IDEMPOTENCY
                .type(TransactionType.CAPTURE_FARE)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(rider)
                .amount(settlementResult.riderPay().amount())
                .currency(CURRENCY)
                .sharedRide(sharedRide) // ✅ FIX: Required for database constraint txn_booking_required_for_ride
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(riderAvailableBefore) // Snapshot for audit
                .afterAvail(riderAfterAvail) // Snapshot for audit
                .beforePending(riderPendingBefore) // Snapshot for audit
                .afterPending(riderAfterPending) // Snapshot for audit
                .note("Rider payment for ride " + request.getRideRequestId())
                .build();

        // ✅ SSOT: Create driver payout transaction (IN)
        Transaction driverPayoutTxn = Transaction.builder()
                .groupId(groupId)
                .wallet(lockedDriverWallet) // ✅ Set wallet relationship (use locked wallet)
                .type(TransactionType.CAPTURE_FARE)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(driver)
                .amount(settlementResult.driverPayout().amount())
                .currency(CURRENCY)
                .sharedRide(sharedRide) // ✅ FIX: Required for database constraint txn_booking_required_for_ride
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(driverAvailableBefore) // Snapshot for audit
                .afterAvail(driverAfterAvail) // Snapshot for audit
                .beforePending(driverPendingBefore) // Snapshot for audit
                .afterPending(driverAfterPending) // Snapshot for audit
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
                .sharedRide(sharedRide) // ✅ FIX: Required for database constraint txn_booking_required_for_ride
                .status(TransactionStatus.SUCCESS)
                .note("Platform commission for ride " + request.getRideRequestId())
                .build();

        transactionRepository.save(riderPayTxn);
        transactionRepository.save(driverPayoutTxn);
        transactionRepository.save(commissionTxn);

        // ✅ SSOT: KHÔNG update wallet balance trực tiếp
        // Balance sẽ được tính từ transactions table khi query

        // ✅ FIX P0-BALANCE_CACHE: Invalidate cache sau khi capture fare
        balanceCalculationService.invalidateBalanceCache(lockedRiderWallet.getWalletId());
        balanceCalculationService.invalidateBalanceCache(lockedDriverWallet.getWalletId());

        String riderPayloadJson = String.format(
                "{\"oldWalletBalance\": %s, \"newWalletBalance\": %s, \"capturedAmount\": %s, \"bookingId\": %d}",
                riderAvailableBefore,
                riderAfterAvail,
                settlementResult.riderPay().amount(),
                request.getRideRequestId());

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
                request.getRideRequestId());

        notificationService.sendNotification(driver,
                NotificationType.WALLET_PAYOUT,
                "You've Been Paid",
                String.format("You received %,.0f VND for completing a ride.",
                        settlementResult.driverPayout().amount()),
                driverPayloadJson,
                Priority.MEDIUM,
                DeliveryMethod.IN_APP,
                null);

        return new RideRequestSettledResponse(
                settlementResult.driverPayout().amount(),
                settlementResult.commission().amount());
    }

    @Override
    @Transactional
    public void releaseRideFunds(RideHoldReleaseRequest request) {
        // Find the original hold transaction
        Transaction transaction = transactionRepository.findAll().stream()
                .filter(t -> t.getSharedRideRequest() != null
                        && t.getSharedRideRequest().getSharedRideRequestId().equals(request.getRideRequestId()))
                .filter(t -> t.getActorUser() != null && t.getActorUser().getUserId().equals(request.getRiderId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Transaction not found for request: " + request.getRiderId()));

        if (transaction.getGroupId() == null) {
            throw new NotFoundException("Group ID not found for bookingId: " + request.getRiderId());
        }
        UUID groupId = transaction.getGroupId();

        Transaction releaseTxn = walletService.releaseHold(
                groupId,
                request.getNote() != null ? request.getNote()
                        : "Release hold for ride request #" + request.getRideRequestId());

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
                availableBalance.subtract(heldAmount), // Before release
                availableBalance, // After release
                heldAmount,
                request.getRideRequestId());

        User rider = userRepository.findById(riderId).orElseThrow(() -> BaseDomainException.of("user.not-found.by-id"));
        notificationService.sendNotification(rider,
                NotificationType.WALLET_RELEASE,
                "Hold Released",
                String.format("The hold of %,.2f VND for booking request #%d has been released.", heldAmount,
                        request.getRideRequestId()),
                riderPayloadJson,
                Priority.MEDIUM,
                DeliveryMethod.IN_APP,
                null);
    }
}
