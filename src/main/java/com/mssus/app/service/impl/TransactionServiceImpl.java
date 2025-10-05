package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.TransactionService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public UUID generateGroupId() {
        return UUID.randomUUID();
    }

    // ========== RIDER_TOPUP FLOWS ==========

    @Override
    @Transactional
    public List<Transaction> initTopup(Integer userId, BigDecimal amount, String pspRef, String description) {
        validateTopupInput(userId, amount, pspRef);

        // Check for duplicate PSP reference
        List<Transaction> existingTransactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (!existingTransactions.isEmpty()) {
            throw new ValidationException("Transaction with PSP reference " + pspRef + " already exists");
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for userId: " + userId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found for userId: " + userId));

        UUID groupId = generateGroupId();

        // Create system inflow transaction (PSP -> Master Wallet)
        Transaction systemInflow = Transaction.builder()
                .type(TransactionType.TOPUP)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.PSP)
                .systemWallet(SystemWallet.MASTER)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .note("PSP Inflow - " + description)
                .build();

        // Create user credit transaction (User receives pending balance)
        Transaction userCredit = Transaction.builder()
                .type(TransactionType.TOPUP)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .riderUser(user)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .beforeAvail(wallet.getShadowBalance())
                .afterAvail(wallet.getShadowBalance())
                .beforePending(wallet.getPendingBalance())
                .afterPending(wallet.getPendingBalance().add(amount))
                .pspRef(pspRef)
                .note(description)
                .build();

        // Save transactions
        List<Transaction> transactions = Arrays.asList(
                transactionRepository.save(systemInflow),
                transactionRepository.save(userCredit)
        );

        // Update wallet pending balance
        walletService.increasePendingBalance(userId, amount);

        log.info("Initiated top-up for user {} with amount {} and pspRef {}", userId, amount, pspRef);
        return transactions;
    }

    @Override
    @Transactional
    public void handleTopupSuccess(String pspRef) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending transactions found for pspRef: " + pspRef);
        }

        Integer userId = null;
        BigDecimal amount = null;
        Integer userTxnId = null;

        // Update all transactions to SUCCESS
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);

            if (txn.getActorKind() == ActorKind.USER && txn.getActorUser() != null) {
                userId = txn.getActorUser().getUserId();
                amount = txn.getAmount();
                userTxnId = txn.getTxnId();

                // Update final balances
                Integer finalUserId = userId;
                Wallet wallet = walletRepository.findByUser_UserId(userId)
                        .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + finalUserId));
                txn.setAfterAvail(wallet.getShadowBalance().add(amount));
                txn.setAfterPending(wallet.getPendingBalance().subtract(amount));
            }

            transactionRepository.save(txn);
        }

        // Transfer pending to available balance
        if (userId != null && amount != null) {
            walletService.transferPendingToAvailable(userId, amount);
            sendTopupSuccessEmail(userId, amount, userTxnId);
        }

        log.info("Top-up success for pspRef: {}", pspRef);
    }

    @Override
    @Transactional
    public void handleTopupFailed(String pspRef, String reason) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending transactions found for pspRef: " + pspRef);
        }

        Integer userId = null;
        BigDecimal amount = null;
        Integer userTxnId = null;

        // Update all transactions to FAILED
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);

            if (txn.getActorKind() == ActorKind.USER && txn.getActorUser() != null) {
                userId = txn.getActorUser().getUserId();
                amount = txn.getAmount();
                userTxnId = txn.getTxnId();
            }

            transactionRepository.save(txn);
        }

        // Remove pending balance
        if (userId != null && amount != null) {
            walletService.decreasePendingBalance(userId, amount);
            sendTopupFailedEmail(userId, amount, userTxnId, reason);
        }

        log.info("Top-up failed for pspRef: {} with reason: {}", pspRef, reason);
    }

    // ========== RIDE_HOLD FLOWS ==========

    @Override
    @Transactional
    public List<Transaction> createHold(Integer riderId, BigDecimal amount, Integer bookingId, String description) {
        validateHoldInput(riderId, amount, bookingId);

        Wallet wallet = walletRepository.findByUser_UserId(riderId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for riderId: " + riderId));
        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new NotFoundException("Rider not found for riderId: " + riderId));

        // Check sufficient balance
        if (wallet.getShadowBalance().compareTo(amount) < 0) {
            throw new ValidationException("Insufficient balance. Available: " + wallet.getShadowBalance() + ", Required: " + amount);
        }

        UUID groupId = generateGroupId();

        // Create hold transaction (moves from available to pending)
        Transaction holdTransaction = Transaction.builder()
                .type(TransactionType.HOLD_CREATE)
                .groupId(groupId)
                .direction(TransactionDirection.INTERNAL)
                .actorKind(ActorKind.SYSTEM)
                .riderUser(rider)
                .amount(amount)
                .currency("VND")
                .bookingId(bookingId)
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(wallet.getShadowBalance())
                .afterAvail(wallet.getShadowBalance().subtract(amount))
                .beforePending(wallet.getPendingBalance())
                .afterPending(wallet.getPendingBalance().add(amount))
                .note(description)
                .build();

        Transaction savedTransaction = transactionRepository.save(holdTransaction);

        // Update wallet balances
        wallet.setShadowBalance(wallet.getShadowBalance().subtract(amount));
        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        walletRepository.save(wallet);

        log.info("Created hold for rider {} with amount {} for booking {}", riderId, amount, bookingId);
        return Arrays.asList(savedTransaction);
    }

    // ========== RIDE_CAPTURE FLOWS ==========

    @Override
    @Transactional
    public List<Transaction> captureFare(UUID groupId, Integer riderId, Integer driverId, BigDecimal totalFare,
                                        BigDecimal commissionRate, String description) {
        validateCaptureInput(groupId, riderId, driverId, totalFare, commissionRate);

        // Find the original hold transaction
        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
        Transaction holdTransaction = holdTransactions.stream()
                .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));

        BigDecimal heldAmount = holdTransaction.getAmount();
        if (totalFare.compareTo(heldAmount) > 0) {
            throw new ValidationException("Capture amount exceeds held amount. Held: " + heldAmount + ", Capture: " + totalFare);
        }

        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));

        User rider = userRepository.findById(riderId).orElseThrow(() -> new NotFoundException("Rider not found"));
        User driver = userRepository.findById(driverId).orElseThrow(() -> new NotFoundException("Driver not found"));

        BigDecimal commission = calculateCommission(totalFare, commissionRate);
        BigDecimal driverAmount = totalFare.subtract(commission);
        BigDecimal releaseAmount = heldAmount.subtract(totalFare);

        List<Transaction> transactions = new ArrayList<>();

        // 1. Capture fare from rider's pending balance
        Transaction riderCharge = Transaction.builder()
                .type(TransactionType.CAPTURE_FARE)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
                .riderUser(rider)
                .amount(totalFare)
                .currency("VND")
                .bookingId(holdTransaction.getBookingId())
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(riderWallet.getShadowBalance())
                .afterAvail(riderWallet.getShadowBalance())
                .beforePending(riderWallet.getPendingBalance())
                .afterPending(riderWallet.getPendingBalance().subtract(totalFare))
                .note("Fare capture - " + description)
                .build();

        // 2. Pay driver
        Transaction driverCredit = Transaction.builder()
                .type(TransactionType.CAPTURE_FARE)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .driverUser(driver)
                .amount(driverAmount)
                .currency("VND")
                .bookingId(holdTransaction.getBookingId())
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(driverWallet.getShadowBalance())
                .afterAvail(driverWallet.getShadowBalance().add(driverAmount))
                .beforePending(driverWallet.getPendingBalance())
                .afterPending(driverWallet.getPendingBalance())
                .note("Driver payment - " + description)
                .build();

        // 3. Commission to system
        Transaction commissionTransaction = Transaction.builder()
                .type(TransactionType.CAPTURE_FARE)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.COMMISSION)
                .amount(commission)
                .currency("VND")
                .bookingId(holdTransaction.getBookingId())
                .status(TransactionStatus.SUCCESS)
                .note("Commission - " + description)
                .build();

        transactions.add(transactionRepository.save(riderCharge));
        transactions.add(transactionRepository.save(driverCredit));
        transactions.add(transactionRepository.save(commissionTransaction));

        // 4. Release remaining hold if any
        if (releaseAmount.compareTo(BigDecimal.ZERO) > 0) {
            Transaction releaseTransaction = Transaction.builder()
                    .type(TransactionType.HOLD_RELEASE)
                    .groupId(groupId)
                    .direction(TransactionDirection.INTERNAL)
                    .actorKind(ActorKind.SYSTEM)
                    .riderUser(rider)
                    .amount(releaseAmount)
                    .currency("VND")
                    .bookingId(holdTransaction.getBookingId())
                    .status(TransactionStatus.SUCCESS)
                    .beforeAvail(riderWallet.getShadowBalance())
                    .afterAvail(riderWallet.getShadowBalance().add(releaseAmount))
                    .beforePending(riderWallet.getPendingBalance().subtract(totalFare))
                    .afterPending(riderWallet.getPendingBalance().subtract(heldAmount))
                    .note("Hold release - " + description)
                    .build();

            transactions.add(transactionRepository.save(releaseTransaction));

            // Update rider wallet - release remaining amount
            riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(releaseAmount));
        }

        // Update wallet balances
        riderWallet.setPendingBalance(riderWallet.getPendingBalance().subtract(heldAmount));
        riderWallet.setTotalSpent(riderWallet.getTotalSpent().add(totalFare));

        driverWallet.setShadowBalance(driverWallet.getShadowBalance().add(driverAmount));

        walletRepository.save(riderWallet);
        walletRepository.save(driverWallet);

        log.info("Captured fare for booking {} - Total: {}, Driver: {}, Commission: {}",
                holdTransaction.getBookingId(), totalFare, driverAmount, commission);

        return transactions;
    }

    // ========== RIDE_CANCEL FLOWS ==========

    @Override
    @Transactional
    public List<Transaction> releaseHold(UUID groupId, String description) {
        if (groupId == null) {
            throw new ValidationException("Group ID cannot be null");
        }

        // Find the original hold transaction
        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
        Transaction holdTransaction = holdTransactions.stream()
                .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));

        // Check if already released
        boolean alreadyReleased = holdTransactions.stream()
                .anyMatch(t -> t.getType() == TransactionType.HOLD_RELEASE);
        if (alreadyReleased) {
            throw new ValidationException("Hold has already been released for groupId: " + groupId);
        }

        BigDecimal heldAmount = holdTransaction.getAmount();
        Integer riderId = holdTransaction.getRiderUser().getUserId();

        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));

        // Create release transaction
        Transaction releaseTransaction = Transaction.builder()
                .type(TransactionType.HOLD_RELEASE)
                .groupId(groupId)
                .direction(TransactionDirection.INTERNAL)
                .actorKind(ActorKind.SYSTEM)
                .riderUser(holdTransaction.getRiderUser())
                .amount(heldAmount)
                .currency("VND")
                .bookingId(holdTransaction.getBookingId())
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(riderWallet.getShadowBalance())
                .afterAvail(riderWallet.getShadowBalance().add(heldAmount))
                .beforePending(riderWallet.getPendingBalance())
                .afterPending(riderWallet.getPendingBalance().subtract(heldAmount))
                .note(description)
                .build();

        Transaction savedTransaction = transactionRepository.save(releaseTransaction);

        // Update rider wallet balances
        riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(heldAmount));
        riderWallet.setPendingBalance(riderWallet.getPendingBalance().subtract(heldAmount));
        walletRepository.save(riderWallet);

        log.info("Released hold for groupId {} with amount {}", groupId, heldAmount);
        return Arrays.asList(savedTransaction);
    }

    // ========== DRIVER_PAYOUT FLOWS ==========

    @Override
    @Transactional
    public List<Transaction> initPayout(Integer driverId, BigDecimal amount, String pspRef, String description) {
        validatePayoutInput(driverId, amount, pspRef);

        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found"));

        // Check sufficient balance
        if (driverWallet.getShadowBalance().compareTo(amount) < 0) {
            throw new ValidationException("Insufficient balance for payout. Available: " + driverWallet.getShadowBalance());
        }

        UUID groupId = generateGroupId();

        // Create payout transaction (pending until PSP confirms)
        Transaction payoutTransaction = Transaction.builder()
                .type(TransactionType.PAYOUT_SUCCESS)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.PSP)
                .driverUser(driver)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .beforeAvail(driverWallet.getShadowBalance())
                .afterAvail(driverWallet.getShadowBalance().subtract(amount))
                .beforePending(driverWallet.getPendingBalance())
                .afterPending(driverWallet.getPendingBalance().add(amount))
                .note(description)
                .build();

        Transaction savedTransaction = transactionRepository.save(payoutTransaction);

        // Move money from available to pending
        driverWallet.setShadowBalance(driverWallet.getShadowBalance().subtract(amount));
        driverWallet.setPendingBalance(driverWallet.getPendingBalance().add(amount));
        walletRepository.save(driverWallet);

        log.info("Initiated payout for driver {} with amount {} and pspRef {}", driverId, amount, pspRef);
        return Arrays.asList(savedTransaction);
    }

    @Override
    @Transactional
    public void handlePayoutSuccess(String pspRef) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending payout transactions found for pspRef: " + pspRef);
        }

        Integer driverId = null;
        BigDecimal amount = null;

        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);

            if (txn.getDriverUser() != null) {
                driverId = txn.getDriverUser().getUserId();
                amount = txn.getAmount();
            }

            transactionRepository.save(txn);
        }

        // Remove pending balance (money has been paid out)
        if (driverId != null && amount != null) {
            walletService.decreasePendingBalance(driverId, amount);
        }

        log.info("Payout success for pspRef: {}", pspRef);
    }

    @Override
    @Transactional
    public void handlePayoutFailed(String pspRef, String reason) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending payout transactions found for pspRef: " + pspRef);
        }

        Integer driverId = null;
        BigDecimal amount = null;

        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);

            if (txn.getDriverUser() != null) {
                driverId = txn.getDriverUser().getUserId();
                amount = txn.getAmount();
            }

            transactionRepository.save(txn);
        }

        // Return money from pending to available
        if (driverId != null && amount != null) {
            Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                    .orElseThrow(() -> new NotFoundException("Driver wallet not found"));

            driverWallet.setShadowBalance(driverWallet.getShadowBalance().add(amount));
            driverWallet.setPendingBalance(driverWallet.getPendingBalance().subtract(amount));
            walletRepository.save(driverWallet);
        }

        log.info("Payout failed for pspRef: {} with reason: {}", pspRef, reason);
    }

    // ========== RIDE_REFUND FLOWS ==========

    @Override
    @Transactional
    public List<Transaction> refundRide(UUID originalGroupId, Integer riderId, Integer driverId,
                                       BigDecimal refundAmount, String description) {
        validateRefundInput(originalGroupId, riderId, driverId, refundAmount);

        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));

        User rider = userRepository.findById(riderId).orElseThrow(() -> new NotFoundException("Rider not found"));
        User driver = userRepository.findById(driverId).orElseThrow(() -> new NotFoundException("Driver not found"));

        // Check if driver has sufficient balance
        if (driverWallet.getShadowBalance().compareTo(refundAmount) < 0) {
            throw new ValidationException("Driver has insufficient balance for refund. Available: " + driverWallet.getShadowBalance());
        }

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // 1. Credit rider
        Transaction riderCredit = Transaction.builder()
                .type(TransactionType.ADJUSTMENT) // Using ADJUSTMENT for refunds
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .riderUser(rider)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(riderWallet.getShadowBalance())
                .afterAvail(riderWallet.getShadowBalance().add(refundAmount))
                .beforePending(riderWallet.getPendingBalance())
                .afterPending(riderWallet.getPendingBalance())
                .note("Refund credit - " + description)
                .build();

        // 2. Debit driver
        Transaction driverDebit = Transaction.builder()
                .type(TransactionType.ADJUSTMENT)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
                .driverUser(driver)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(driverWallet.getShadowBalance())
                .afterAvail(driverWallet.getShadowBalance().subtract(refundAmount))
                .beforePending(driverWallet.getPendingBalance())
                .afterPending(driverWallet.getPendingBalance())
                .note("Refund debit - " + description)
                .build();

        transactions.add(transactionRepository.save(riderCredit));
        transactions.add(transactionRepository.save(driverDebit));

        // Update wallet balances
        riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(refundAmount));
        driverWallet.setShadowBalance(driverWallet.getShadowBalance().subtract(refundAmount));

        walletRepository.save(riderWallet);
        walletRepository.save(driverWallet);

        log.info("Processed refund for original groupId {} - Amount: {}", originalGroupId, refundAmount);
        return transactions;
    }

    // ========== PROMO_CREDIT FLOWS ==========

    @Override
    @Transactional
    public Transaction creditPromo(Integer userId, BigDecimal amount, String promoCode, String description) {
        validatePromoInput(userId, amount, promoCode);

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UUID groupId = generateGroupId();

        // Create promo credit transaction
        Transaction promoTransaction = Transaction.builder()
                .type(TransactionType.PROMO_CREDIT)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.PROMO)
                .actorUser(user)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(wallet.getShadowBalance())
                .afterAvail(wallet.getShadowBalance().add(amount))
                .beforePending(wallet.getPendingBalance())
                .afterPending(wallet.getPendingBalance())
                .note("Promo credit: " + promoCode + " - " + description)
                .build();

        Transaction savedTransaction = transactionRepository.save(promoTransaction);

        // Update wallet balance
        wallet.setShadowBalance(wallet.getShadowBalance().add(amount));
        walletRepository.save(wallet);

        log.info("Credited promo {} to user {} with amount {}", promoCode, userId, amount);
        return savedTransaction;
    }

    // ========== ADMIN_ADJUSTMENT FLOWS ==========

    @Override
    @Transactional
    public Transaction adjustment(Integer userId, BigDecimal amount, Integer adminUserId, String reason) {
        validateAdjustmentInput(userId, amount, adminUserId, reason);

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));

        UUID groupId = generateGroupId();
        TransactionDirection direction = amount.compareTo(BigDecimal.ZERO) >= 0 ?
                TransactionDirection.IN : TransactionDirection.OUT;

        // For negative adjustments, check sufficient balance
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal absAmount = amount.abs();
            if (wallet.getShadowBalance().compareTo(absAmount) < 0) {
                throw new ValidationException("Insufficient balance for adjustment. Available: " + wallet.getShadowBalance());
            }
        }

        Transaction adjustmentTransaction = Transaction.builder()
                .type(TransactionType.ADJUSTMENT)
                .groupId(groupId)
                .direction(direction)
                .actorKind(ActorKind.SYSTEM)
                .actorUser(admin) // Admin who made the adjustment
                .riderUser(user)  // User being adjusted
                .amount(amount.abs())
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(wallet.getShadowBalance())
                .afterAvail(wallet.getShadowBalance().add(amount))
                .beforePending(wallet.getPendingBalance())
                .afterPending(wallet.getPendingBalance())
                .note("Admin adjustment by user " + adminUserId + ": " + reason)
                .build();

        Transaction savedTransaction = transactionRepository.save(adjustmentTransaction);

        // Update wallet balance
        wallet.setShadowBalance(wallet.getShadowBalance().add(amount));
        walletRepository.save(wallet);

        log.info("Admin {} made adjustment for user {} with amount {}: {}", adminUserId, userId, amount, reason);
        return savedTransaction;
    }

    // ========== UTILITY METHODS ==========

    @Override
    public List<Transaction> getTransactionsByGroupId(UUID groupId) {
        if (groupId == null) {
            throw new ValidationException("Group ID cannot be null");
        }
        return transactionRepository.findByGroupId(groupId);
    }

    @Override
    public BigDecimal calculateCommission(BigDecimal amount, BigDecimal commissionRate) {
        if (amount == null || commissionRate == null) {
            throw new ValidationException("Amount and commission rate cannot be null");
        }
        if (commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException("Commission rate must be between 0 and 1");
        }
        return amount.multiply(commissionRate).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validateTopupInput(Integer userId, BigDecimal amount, String pspRef) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateHoldInput(Integer riderId, BigDecimal amount, Integer bookingId) {
        if (riderId == null) {
            throw new ValidationException("Rider ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (bookingId == null) {
            throw new ValidationException("Booking ID cannot be null");
        }
    }

    private void validateCaptureInput(UUID groupId, Integer riderId, Integer driverId, BigDecimal totalFare, BigDecimal commissionRate) {
        if (groupId == null) {
            throw new ValidationException("Group ID cannot be null");
        }
        if (riderId == null) {
            throw new ValidationException("Rider ID cannot be null");
        }
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (totalFare == null || totalFare.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Total fare must be greater than zero");
        }
        if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new ValidationException("Commission rate must be between 0 and 1");
        }
    }

    private void validatePayoutInput(Integer driverId, BigDecimal amount, String pspRef) {
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateRefundInput(UUID originalGroupId, Integer riderId, Integer driverId, BigDecimal refundAmount) {
        if (originalGroupId == null) {
            throw new ValidationException("Original group ID cannot be null");
        }
        if (riderId == null) {
            throw new ValidationException("Rider ID cannot be null");
        }
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
    }

    private void validatePromoInput(Integer userId, BigDecimal amount, String promoCode) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (promoCode == null || promoCode.trim().isEmpty()) {
            throw new ValidationException("Promo code cannot be null or empty");
        }
    }

    private void validateAdjustmentInput(Integer userId, BigDecimal amount, Integer adminUserId, String reason) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new ValidationException("Adjustment amount cannot be zero");
        }
        if (adminUserId == null) {
            throw new ValidationException("Admin user ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Adjustment reason cannot be null or empty");
        }
    }

    private void sendTopupSuccessEmail(Integer userId, BigDecimal amount, Integer txnId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null) {
                Wallet wallet = walletRepository.findByUser_UserId(userId).orElse(null);
                BigDecimal newBalance = wallet != null ? wallet.getShadowBalance() : BigDecimal.ZERO;

                emailService.sendTopUpSuccessEmail(
                        user.getEmail(),
                        user.getFullName(),
                        amount,
                        String.valueOf(txnId),
                        newBalance
                );

                log.info("Top-up success email sent to user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to send top-up success email to user {}: {}", userId, e.getMessage());
        }
    }

    private void sendTopupFailedEmail(Integer userId, BigDecimal amount, Integer txnId, String reason) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null) {
                emailService.sendPaymentFailedEmail(
                        user.getEmail(),
                        user.getFullName(),
                        amount,
                        String.valueOf(txnId),
                        reason
                );

                log.info("Top-up failed email sent to user {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to send top-up failed email to user {}: {}", userId, e.getMessage());
        }
    }
}
