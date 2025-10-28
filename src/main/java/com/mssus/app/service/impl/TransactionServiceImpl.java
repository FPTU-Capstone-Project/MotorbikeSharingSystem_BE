package com.mssus.app.service.impl;

import com.mssus.app.common.enums.*;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.CreateTransactionRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.wallet.TransactionResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.mapper.TransactionMapper;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.EmailService;
import org.springframework.security.core.Authentication;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.service.TransactionService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionMapper transactionMapper;

    public UUID generateGroupId() {
        return UUID.randomUUID();
    }

    @Override
    @Transactional
    public List<Transaction> initTopup(Integer userId, BigDecimal amount, String pspRef, String description) {
        validateTopupInput(userId, amount, pspRef);

        List<Transaction> existingTransactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (!existingTransactions.isEmpty()) {
            throw new ValidationException("Transaction with PSP reference " + pspRef + " already exists");
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for userId: " + userId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found for userId: " + userId));

        UUID groupId = generateGroupId();

        Transaction systemInflow = Transaction.builder()
                .type(TransactionType.TOPUP)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .note("PSP Inflow - " + description)
                .build();

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

        List<Transaction> transactions = Arrays.asList(
                transactionRepository.save(systemInflow),
                transactionRepository.save(userCredit)
        );

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

    @Override
    @Transactional
    public Transaction createTransaction(CreateTransactionRequest request) {
        TransactionType type = request.type();
        TransactionDirection direction = request.direction();
        ActorKind actorKind = request.actorKind();
        Integer actorUserId = request.actorUserId();
        SystemWallet systemWallet = request.systemWallet();
        BigDecimal amount = request.amount();
        String currency = request.currency();
        UUID groupId = request.groupId();
        Integer bookingId = request.bookingId();
        Integer riderUserId = request.riderUserId();
        Integer driverUserId = request.driverUserId();
        String pspRef = request.pspRef();
        TransactionStatus status = request.status();
        String note = request.note();
        BigDecimal beforeAvail = request.beforeAvail();
        BigDecimal afterAvail = request.afterAvail();
        BigDecimal beforePending = request.beforePending();
        BigDecimal afterPending = request.afterPending();

        validateCreateTransactionRequest(request);
        User actorUser = null;
        User riderUser = (riderUserId != null) ? userRepository.findById(riderUserId)
            .orElseThrow(() -> new NotFoundException("Rider user not found: " + riderUserId)) : null;
        User driverUser = (driverUserId != null) ? userRepository.findById(driverUserId)
            .orElseThrow(() -> new NotFoundException("Driver user not found: " + driverUserId)) : null;

        if (actorKind == ActorKind.USER) {
            actorUser = userRepository.findById(actorUserId)
                .orElseThrow(() -> new NotFoundException("Actor user not found: " + actorUserId));
            if (request.beforeAvail() == null || request.afterAvail() == null ||
                request.beforePending() == null || request.afterPending() == null) {
                throw new ValidationException("Balance snapshots (before/after) are required for USER transactions.");
            }
        } else {
            beforeAvail = null;
            afterAvail = null;
            beforePending = null;
            afterPending = null;
        }

        Transaction transaction = Transaction.builder()
            .groupId(groupId)
            .type(type)
            .direction(direction)
            .actorKind(actorKind)
            .actorUser(actorUser)
            .systemWallet(systemWallet)
            .amount(amount)
            .currency(currency)
            .bookingId(bookingId)
            .riderUser(riderUser)
            .driverUser(driverUser)
            .pspRef(pspRef)
            .status(status)
            .beforeAvail(beforeAvail)
            .afterAvail(afterAvail)
            .beforePending(beforePending)
            .afterPending(afterPending)
            .note(note)
            .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        log.info("Created transaction - ID: {}, type: {}, actor: {}, amount: {}",
            savedTransaction.getTxnId(), type, actorKind, amount);

        return savedTransaction;
    }


//    @Override
//    @Transactional
//    public List<Transaction> createHold(Integer riderId, BigDecimal amount, Integer bookingId, String description) {
//        validateHoldInput(riderId, amount, bookingId);
//
//        Wallet wallet = walletRepository.findByUser_UserId(riderId)
//                .orElseThrow(() -> new NotFoundException("Wallet not found for riderId: " + riderId));
//        User rider = userRepository.findById(riderId)
//                .orElseThrow(() -> new NotFoundException("Rider not found for riderId: " + riderId));
//
//        // Check sufficient balance
//        if (wallet.getShadowBalance().compareTo(amount) < 0) {
//            throw new ValidationException("Insufficient balance. Available: " + wallet.getShadowBalance() + ", Required: " + amount);
//        }
//
//        UUID groupId = generateGroupId();
//
//        // Create hold transaction (moves from available to pending)
//        Transaction holdTransaction = Transaction.builder()
//                .type(TransactionType.HOLD_CREATE)
//                .groupId(groupId)
//                .direction(TransactionDirection.INTERNAL)
//                .actorKind(ActorKind.USER)
//                .actorUser(rider.getRiderProfile().getUser())
//                .riderUser(rider)
//                .amount(amount)
//                .currency("VND")
//                .bookingId(bookingId)
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(wallet.getShadowBalance())
//                .afterAvail(wallet.getShadowBalance().subtract(amount))
//                .beforePending(wallet.getPendingBalance())
//                .afterPending(wallet.getPendingBalance().add(amount))
//                .note(description)
//                .build();
//
//        Transaction savedTransaction = transactionRepository.save(holdTransaction);
//
//        // Update wallet balances
//        wallet.setShadowBalance(wallet.getShadowBalance().subtract(amount));
//        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
//        walletRepository.save(wallet);
//
//        log.info("Created hold for rider {} with amount {} for booking {}", riderId, amount, bookingId);
//        return Arrays.asList(savedTransaction);
//    }
//
//    @Override
//    @Transactional
//    public List<Transaction> captureFare(SettlementResult settlementResult, Integer riderId, Integer driverId, String description) {
//        validateCaptureInput(riderId, driverId, settlementResult.driverPayout());
//
//        // Find the original hold transaction
//        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
//        Transaction holdTransaction = holdTransactions.stream()
//                .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
//                .findFirst()
//                .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));
//
//        BigDecimal heldAmount = holdTransaction.getAmount();
//        if (totalFare.compareTo(heldAmount) > 0) {
//            throw new ValidationException("Capture amount exceeds held amount. Held: " + heldAmount + ", Capture: " + totalFare);
//        }
//
//        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
//                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
//        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
//                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
//
//        User rider = userRepository.findById(riderId).orElseThrow(() -> new NotFoundException("Rider not found"));
//        User driver = userRepository.findById(driverId).orElseThrow(() -> new NotFoundException("Driver not found"));
//
//        BigDecimal commission = calculateCommission(totalFare, commissionRate);
//        BigDecimal driverAmount = totalFare.subtract(commission);
//        BigDecimal releaseAmount = heldAmount.subtract(totalFare);
//
//        List<Transaction> transactions = new ArrayList<>();
//
//        // 1. Capture fare from rider's pending balance
//        Transaction riderCharge = Transaction.builder()
//                .type(TransactionType.CAPTURE_FARE)
//                .groupId(groupId)
//                .direction(TransactionDirection.OUT)
//                .actorKind(ActorKind.USER)
//                .actorUser(rider.getRiderProfile().getUser())
//                .riderUser(rider)
//                .amount(totalFare)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(riderWallet.getShadowBalance())
//                .afterAvail(riderWallet.getShadowBalance())
//                .beforePending(riderWallet.getPendingBalance())
//                .afterPending(riderWallet.getPendingBalance().subtract(totalFare))
//                .note("Fare capture - " + description)
//                .build();
//
//        // 2. Pay driver
//        Transaction driverCredit = Transaction.builder()
//                .type(TransactionType.CAPTURE_FARE)
//                .groupId(groupId)
//                .direction(TransactionDirection.IN)
//                .actorUser(driver.getDriverProfile().getUser())
//                .actorKind(ActorKind.USER)
//                .driverUser(driver)
//                .amount(driverAmount)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(driverWallet.getShadowBalance())
//                .afterAvail(driverWallet.getShadowBalance().add(driverAmount))
//                .beforePending(driverWallet.getPendingBalance())
//                .afterPending(driverWallet.getPendingBalance())
//                .note("Driver payment - " + description)
//                .build();
//
//        // 3. Commission to system
//        Transaction commissionTransaction = Transaction.builder()
//                .type(TransactionType.CAPTURE_FARE)
//                .groupId(groupId)
//                .direction(TransactionDirection.IN)
//                .actorKind(ActorKind.SYSTEM)
//                .systemWallet(SystemWallet.COMMISSION)
//                .amount(commission)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .note("Commission - " + description)
//                .build();
//
//        transactions.add(transactionRepository.save(riderCharge));
//        transactions.add(transactionRepository.save(driverCredit));
//        transactions.add(transactionRepository.save(commissionTransaction));
//
//        // 4. Release remaining hold if any
//        if (releaseAmount.compareTo(BigDecimal.ZERO) > 0) {
//            Transaction releaseTransaction = Transaction.builder()
//                    .type(TransactionType.HOLD_RELEASE)
//                    .groupId(groupId)
//                    .direction(TransactionDirection.INTERNAL)
//                    .actorKind(ActorKind.SYSTEM)
//                    .riderUser(rider)
//                    .amount(releaseAmount)
//                    .currency("VND")
//                    .bookingId(holdTransaction.getBookingId())
//                    .status(TransactionStatus.SUCCESS)
//                    .beforeAvail(riderWallet.getShadowBalance())
//                    .afterAvail(riderWallet.getShadowBalance().add(releaseAmount))
//                    .beforePending(riderWallet.getPendingBalance().subtract(totalFare))
//                    .afterPending(riderWallet.getPendingBalance().subtract(heldAmount))
//                    .note("Hold release - " + description)
//                    .build();
//
//            transactions.add(transactionRepository.save(releaseTransaction));
//
//            // Update rider wallet - release remaining amount
//            riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(releaseAmount));
//        }
//
//        // Update wallet balances
//        riderWallet.setPendingBalance(riderWallet.getPendingBalance().subtract(heldAmount));
//        riderWallet.setTotalSpent(riderWallet.getTotalSpent().add(totalFare));
//
//        driverWallet.setShadowBalance(driverWallet.getShadowBalance().add(driverAmount));
//
//        walletRepository.save(riderWallet);
//        walletRepository.save(driverWallet);
//
//        log.info("Captured fare for booking {} - Total: {}, Driver: {}, Commission: {}",
//                holdTransaction.getBookingId(), totalFare, driverAmount, commission);
//
//        return transactions;
//    }
//
//    @Override
//    @Transactional
//    public List<Transaction> releaseHold(UUID groupId, String description) {
//        if (groupId == null) {
//            throw new ValidationException("Group ID cannot be null");
//        }
//
//        // Find the original hold transaction
//        List<Transaction> holdTransactions = transactionRepository.findByGroupIdAndStatus(groupId, TransactionStatus.SUCCESS);
//        Transaction holdTransaction = holdTransactions.stream()
//                .filter(t -> t.getType() == TransactionType.HOLD_CREATE)
//                .findFirst()
//                .orElseThrow(() -> new NotFoundException("Hold transaction not found for groupId: " + groupId));
//
//        // Check if already released
//        boolean alreadyReleased = holdTransactions.stream()
//                .anyMatch(t -> t.getType() == TransactionType.HOLD_RELEASE);
//        if (alreadyReleased) {
//            throw new ValidationException("Hold has already been released for groupId: " + groupId);
//        }
//
//        BigDecimal heldAmount = holdTransaction.getAmount();
//        Integer riderId = holdTransaction.getRiderUser().getUserId();
//
//        Wallet riderWallet = walletRepository.findByUser_UserId(riderId)
//                .orElseThrow(() -> new NotFoundException("Rider wallet not found"));
//
//        // Create release transaction
//        Transaction releaseTransaction = Transaction.builder()
//                .type(TransactionType.HOLD_RELEASE)
//                .groupId(groupId)
//                .direction(TransactionDirection.INTERNAL)
//                .actorKind(ActorKind.USER)
//                .actorUser(holdTransaction.getActorUser())
//                .riderUser(holdTransaction.getRiderUser())
//                .amount(heldAmount)
//                .currency("VND")
//                .bookingId(holdTransaction.getBookingId())
//                .status(TransactionStatus.SUCCESS)
//                .beforeAvail(riderWallet.getShadowBalance())
//                .afterAvail(riderWallet.getShadowBalance().add(heldAmount))
//                .beforePending(riderWallet.getPendingBalance())
//                .afterPending(riderWallet.getPendingBalance().subtract(heldAmount))
//                .note(description)
//                .build();
//
//        Transaction savedTransaction = transactionRepository.save(releaseTransaction);
//
//        // Update rider wallet balances
//        riderWallet.setShadowBalance(riderWallet.getShadowBalance().add(heldAmount));
//        riderWallet.setPendingBalance(riderWallet.getPendingBalance().subtract(heldAmount));
//        walletRepository.save(riderWallet);
//
//        log.info("Released hold for groupId {} with amount {}", groupId, heldAmount);
//        return Arrays.asList(savedTransaction);
//    }

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
                .type(TransactionType.PAYOUT)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
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
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
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

        if (driverWallet.getShadowBalance().compareTo(refundAmount) < 0) {
            throw new ValidationException("Driver has insufficient balance for refund. Available: " + driverWallet.getShadowBalance());
        }

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        Transaction riderCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(rider.getRiderProfile().getUser())
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
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(driver.getDriverProfile().getUser())
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

    @Override
    @Transactional
    public List<Transaction> refundTopup(Integer userId, BigDecimal refundAmount, String pspRef, String description) {
        validateRefundTopupInput(userId, refundAmount, pspRef);

        Wallet userWallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("User wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if user has sufficient balance for refund
        if (userWallet.getShadowBalance().compareTo(refundAmount) < 0) {
            throw new ValidationException("User has insufficient balance for refund. Available: " + userWallet.getShadowBalance());
        }

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // Create refund transaction (debit from user)
        Transaction userDebit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .beforeAvail(userWallet.getShadowBalance())
                .afterAvail(userWallet.getShadowBalance().subtract(refundAmount))
                .beforePending(userWallet.getPendingBalance())
                .afterPending(userWallet.getPendingBalance().add(refundAmount))
                .note("Topup refund - " + description)
                .build();

        // Create system credit transaction (credit to system master wallet)
        Transaction systemCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .note("System refund credit - " + description)
                .build();

        transactions.add(transactionRepository.save(userDebit));
        transactions.add(transactionRepository.save(systemCredit));

        // Move money from available to pending (until PSP confirms)
        userWallet.setShadowBalance(userWallet.getShadowBalance().subtract(refundAmount));
        userWallet.setPendingBalance(userWallet.getPendingBalance().add(refundAmount));
        walletRepository.save(userWallet);

        log.info("Initiated topup refund for user {} with amount {} and pspRef {}", userId, refundAmount, pspRef);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundPayout(Integer driverId, BigDecimal refundAmount, String pspRef, String description) {
        validateRefundPayoutInput(driverId, refundAmount, pspRef);

        Wallet driverWallet = walletRepository.findByUser_UserId(driverId)
                .orElseThrow(() -> new NotFoundException("Driver wallet not found"));
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver not found"));

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // Create refund transaction (credit to driver)
        Transaction driverCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(driver)
                .driverUser(driver)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .beforeAvail(driverWallet.getShadowBalance())
                .afterAvail(driverWallet.getShadowBalance().add(refundAmount))
                .beforePending(driverWallet.getPendingBalance())
                .afterPending(driverWallet.getPendingBalance())
                .note("Payout refund - " + description)
                .build();

        // Create system debit transaction (debit from system master wallet)
        Transaction systemDebit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .note("System refund debit - " + description)
                .build();

        transactions.add(transactionRepository.save(driverCredit));
        transactions.add(transactionRepository.save(systemDebit));

        // Credit driver's available balance
        driverWallet.setShadowBalance(driverWallet.getShadowBalance().add(refundAmount));
        walletRepository.save(driverWallet);

        log.info("Initiated payout refund for driver {} with amount {} and pspRef {}", driverId, refundAmount, pspRef);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundAdjustment(Integer userId, BigDecimal refundAmount, Integer adminUserId, String reason) {
        validateRefundAdjustmentInput(userId, refundAmount, adminUserId, reason);

        Wallet userWallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("User wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // Create refund transaction (credit to user)
        Transaction userCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(userWallet.getShadowBalance())
                .afterAvail(userWallet.getShadowBalance().add(refundAmount))
                .beforePending(userWallet.getPendingBalance())
                .afterPending(userWallet.getPendingBalance())
                .note("Adjustment refund - " + reason)
                .build();

        // Create system debit transaction (debit from system master wallet)
        Transaction systemDebit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.MASTER)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .note("System adjustment refund - " + reason)
                .build();

        transactions.add(transactionRepository.save(userCredit));
        transactions.add(transactionRepository.save(systemDebit));

        // Credit user's available balance
        userWallet.setShadowBalance(userWallet.getShadowBalance().add(refundAmount));
        walletRepository.save(userWallet);

        log.info("Processed adjustment refund for user {} with amount {} by admin {}", userId, refundAmount, adminUserId);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> refundPromoCredit(Integer userId, BigDecimal refundAmount, String promoCode, String description) {
        validateRefundPromoInput(userId, refundAmount, promoCode);

        Wallet userWallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("User wallet not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UUID groupId = generateGroupId();
        List<Transaction> transactions = new ArrayList<>();

        // Create refund transaction (debit from user)
        Transaction userDebit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.OUT)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .beforeAvail(userWallet.getShadowBalance())
                .afterAvail(userWallet.getShadowBalance().subtract(refundAmount))
                .beforePending(userWallet.getPendingBalance())
                .afterPending(userWallet.getPendingBalance())
                .note("Promo credit refund - " + description)
                .build();

        // Create system credit transaction (credit to promo wallet)
        Transaction promoCredit = Transaction.builder()
                .type(TransactionType.REFUND)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.SYSTEM)
                .systemWallet(SystemWallet.PROMO)
                .amount(refundAmount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .note("Promo wallet refund credit - " + description)
                .build();

        transactions.add(transactionRepository.save(userDebit));
        transactions.add(transactionRepository.save(promoCredit));

        // Debit user's available balance
        userWallet.setShadowBalance(userWallet.getShadowBalance().subtract(refundAmount));
        walletRepository.save(userWallet);

        log.info("Processed promo credit refund for user {} with amount {} and promo code {}", userId, refundAmount, promoCode);
        return transactions;
    }

    @Override
    @Transactional
    public List<Transaction> processRefund(Integer refundId, String pspRef, String description) {
        if (refundId == null) {
            throw new ValidationException("Refund ID cannot be null");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        // Find the original transaction to refund
        Transaction originalTransaction = transactionRepository.findById(refundId)
                .orElseThrow(() -> new NotFoundException("Transaction not found with ID: " + refundId));

        if (originalTransaction.getStatus() != TransactionStatus.SUCCESS) {
            throw new ValidationException("Can only refund successful transactions");
        }

        // Determine refund type based on original transaction type
        switch (originalTransaction.getType()) {
            case TOPUP:
                return refundTopup(originalTransaction.getActorUser().getUserId(), 
                                 originalTransaction.getAmount(), pspRef, description);
            case PAYOUT:
                return refundPayout(originalTransaction.getDriverUser().getUserId(), 
                                   originalTransaction.getAmount(), pspRef, description);
            case CAPTURE_FARE:
                // For ride refunds, we need the original group ID
                return refundRide(originalTransaction.getGroupId(), 
                                 originalTransaction.getRiderUser().getUserId(),
                                 originalTransaction.getDriverUser().getUserId(),
                                 originalTransaction.getAmount(), description);
            default:
                throw new ValidationException("Cannot refund transaction type: " + originalTransaction.getType());
        }
    }

    @Override
    @Transactional
    public void handleRefundSuccess(String pspRef) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending refund transactions found for pspRef: " + pspRef);
        }

        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);
            transactionRepository.save(txn);

            // For topup refunds, move money from pending back to available
            if (txn.getType() == TransactionType.REFUND && txn.getDirection() == TransactionDirection.OUT && 
                txn.getActorUser() != null) {
                walletService.decreasePendingBalance(txn.getActorUser().getUserId(), txn.getAmount());
            }
        }

        log.info("Refund success for pspRef: {}", pspRef);
    }

    @Override
    @Transactional
    public void handleRefundFailed(String pspRef, String reason) {
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Failure reason cannot be null or empty");
        }

        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (transactions.isEmpty()) {
            throw new NotFoundException("No pending refund transactions found for pspRef: " + pspRef);
        }

        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);
            transactionRepository.save(txn);

            // For topup refunds, restore the balance
            if (txn.getType() == TransactionType.REFUND && txn.getDirection() == TransactionDirection.OUT && 
                txn.getActorUser() != null) {
                walletService.increaseShadowBalance(txn.getActorUser().getUserId(), txn.getAmount());
                walletService.decreasePendingBalance(txn.getActorUser().getUserId(), txn.getAmount());
            }
        }

        log.info("Refund failed for pspRef: {} - Reason: {}", pspRef, reason);
    }

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getAllTransactions(Pageable pageable) {
        Page<Transaction> transactionsPage = transactionRepository.findAll(pageable);
        List<TransactionResponse> transactions = transactionsPage.getContent().stream()
                .map(transactionMapper::mapToTransactionResponse)
                .collect(Collectors.toList());
        return buildPageResponse(transactionsPage, transactions);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getUserHistoryTransactions(Authentication authentication,
                                                                        Pageable pageable,
                                                                        String type,
                                                                        String status) {
        Integer userId = extractUserId(authentication);

        TransactionType typeEnum = null;
        if (type != null && !type.isBlank()) {
            try {
                typeEnum = TransactionType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid transaction type: " + type);
            }
        }

        TransactionStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = TransactionStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid transaction status: " + status);
            }
        }

        Page<Transaction> page = transactionRepository.findUserHistory(userId, typeEnum, statusEnum, pageable);
        List<TransactionResponse> items = page.getContent().stream()
                .map(transactionMapper::mapToTransactionResponse)
                .collect(Collectors.toList());
        return buildPageResponse(page, items);
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

    private void validateCaptureInput(Integer riderId, Integer driverId, BigDecimal totalFare, BigDecimal commissionRate) {
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

    private void validateRefundTopupInput(Integer userId, BigDecimal refundAmount, String pspRef) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateRefundPayoutInput(Integer driverId, BigDecimal refundAmount, String pspRef) {
        if (driverId == null) {
            throw new ValidationException("Driver ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }
    }

    private void validateRefundAdjustmentInput(Integer userId, BigDecimal refundAmount, Integer adminUserId, String reason) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (adminUserId == null) {
            throw new ValidationException("Admin user ID cannot be null");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Refund reason cannot be null or empty");
        }
    }

    private void validateRefundPromoInput(Integer userId, BigDecimal refundAmount, String promoCode) {
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Refund amount must be greater than zero");
        }
        if (promoCode == null || promoCode.trim().isEmpty()) {
            throw new ValidationException("Promo code cannot be null or empty");
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

    private Integer extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ValidationException("Unauthenticated request");
        }
        // authentication.getName() is expected to be email; map to userId
        return userRepository.findByEmail(authentication.getName())
                .map(User::getUserId)
                .orElseThrow(() -> new NotFoundException("User not found: " + authentication.getName()));
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

    private void validateCreateTransactionRequest(CreateTransactionRequest request) {
        TransactionType type = request.type();
        TransactionDirection direction = request.direction();
        ActorKind actorKind = request.actorKind();
        Integer actorUserId = request.actorUserId();
        SystemWallet systemWallet = request.systemWallet();
        BigDecimal amount = request.amount();
        String currency = request.currency();
        Integer bookingId = request.bookingId();
        Integer riderUserId = request.riderUserId();
        Integer driverUserId = request.driverUserId();
        TransactionStatus status = request.status();

        if (type == null) {
            throw new ValidationException("Transaction type is required");
        }
        if (direction == null) {
            throw new ValidationException("Transaction direction is required");
        }
        if (actorKind == null) {
            throw new ValidationException("Actor kind is required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }

        if (actorKind == ActorKind.USER && actorUserId == null) {
            throw new ValidationException("Actor user ID is required for USER transactions");
        }
        if (actorKind != ActorKind.USER && actorUserId != null) {
            throw new ValidationException("Actor user ID must be null for non-USER transactions");
        }

        if (actorKind == ActorKind.SYSTEM && systemWallet == null) {
            throw new ValidationException("System wallet is required for SYSTEM transactions");
        }
        if (actorKind != ActorKind.SYSTEM && systemWallet != null) {
            throw new ValidationException("System wallet must be null for non-SYSTEM transactions");
        }

        if ((type == TransactionType.HOLD_CREATE || type == TransactionType.HOLD_RELEASE ||
            type == TransactionType.CAPTURE_FARE) && bookingId == null) {
            throw new ValidationException("Booking ID is required for ride-related transactions: " + type);
        }

        validateTypeCombo(type, direction, actorKind, systemWallet);

        if (type == TransactionType.CAPTURE_FARE) {
            validateCaptureFareAlignment(direction, actorKind, systemWallet, actorUserId, riderUserId, driverUserId);
        }

        validateStatusByType(type, status);
    }

    private void validateTypeCombo(TransactionType type, TransactionDirection direction,
                                   ActorKind actorKind, SystemWallet systemWallet) {
        switch (type) {
            case TOPUP -> {
                boolean validTopup = (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.MASTER && direction == TransactionDirection.IN) ||
                    (actorKind == ActorKind.USER && direction == TransactionDirection.IN);
                if (!validTopup) {
                    throw new ValidationException("Invalid TOPUP combination: must be (SYSTEM/MASTER/IN) or (USER/*/IN)");
                }
            }
            case HOLD_CREATE, HOLD_RELEASE -> {
                if (!(actorKind == ActorKind.USER && direction == TransactionDirection.INTERNAL)) {
                    throw new ValidationException("Invalid " + type + " combination: must be (USER/*/INTERNAL)");
                }
            }
            case CAPTURE_FARE -> {
                boolean validCapture = (actorKind == ActorKind.USER && (direction == TransactionDirection.IN || direction == TransactionDirection.OUT)) ||
                    (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.COMMISSION && direction == TransactionDirection.IN);
                if (!validCapture) {
                    throw new ValidationException("Invalid CAPTURE_FARE combination: must be (USER/*/IN|OUT) or (SYSTEM/COMMISSION/IN)");
                }
            }
            case PAYOUT -> {
                boolean validPayout = (actorKind == ActorKind.USER && direction == TransactionDirection.OUT) ||
                    (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.MASTER && direction == TransactionDirection.OUT);
                if (!validPayout) {
                    throw new ValidationException("Invalid PAYOUT combination: must be (USER/*/OUT) or (SYSTEM/MASTER/OUT)");
                }
            }
//            case PROMO_CREDIT -> {
//                boolean validPromo = (actorKind == ActorKind.SYSTEM && systemWallet == SystemWallet.PROMO && direction == TransactionDirection.OUT) ||
//                    (actorKind == ActorKind.USER && direction == TransactionDirection.IN);
//                if (!validPromo) {
//                    throw new ValidationException("Invalid PROMO_CREDIT combination: must be (SYSTEM/PROMO/OUT) or (USER/*/IN)");
//                }
//            }
            case ADJUSTMENT -> {
                // ADJUSTMENT allows any combination - most flexible
            }
            default -> throw new ValidationException("Unsupported transaction type: " + type);
        }
    }

    private void validateCaptureFareAlignment(TransactionDirection direction, ActorKind actorKind,
                                              SystemWallet systemWallet, Integer actorUserId,
                                              Integer riderUserId, Integer driverUserId) {
        if (actorKind == ActorKind.USER && direction == TransactionDirection.OUT) {
            if (riderUserId == null || !riderUserId.equals(actorUserId)) {
                throw new ValidationException("For CAPTURE_FARE OUT transactions, actor user must be the rider");
            }
        } else if (actorKind == ActorKind.USER && direction == TransactionDirection.IN) {
            if (driverUserId == null || !driverUserId.equals(actorUserId)) {
                throw new ValidationException("For CAPTURE_FARE IN transactions, actor user must be the driver");
            }
        } else if (actorKind == ActorKind.SYSTEM && direction == TransactionDirection.IN) {
            if (systemWallet != SystemWallet.COMMISSION) {
                throw new ValidationException("For CAPTURE_FARE SYSTEM IN transactions, must use COMMISSION wallet");
            }
        }
    }

    private void validateStatusByType(TransactionType type, TransactionStatus status) {
        if (status == null) {
            return;
        }

        switch (type) {
            case TOPUP -> {
                if (status != TransactionStatus.PENDING && status != TransactionStatus.SUCCESS &&
                    status != TransactionStatus.FAILED && status != TransactionStatus.REVERSED) {
                    throw new ValidationException("TOPUP transactions can only have status: PENDING, SUCCESS, FAILED, or REVERSED");
                }
            }
            case PAYOUT -> {
                //implement later
            }
            default -> {
                if (status != TransactionStatus.SUCCESS) {
                    throw new ValidationException(type + " transactions can only have SUCCESS status");
                }
            }
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

    private <T> PageResponse<T> buildPageResponse(Page<?> page, List<T> content) {
        return PageResponse.<T>builder()
                .data(content)
                .pagination(PageResponse.PaginationInfo.builder()
                        .page(page.getNumber() + 1)
                        .pageSize(page.getSize())
                        .totalPages(page.getTotalPages())
                        .totalRecords(page.getTotalElements())
                        .build())
                .build();
    }
}
