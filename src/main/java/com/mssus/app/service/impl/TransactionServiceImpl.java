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

    @Override
    @Transactional
    public Transaction createPendingTopUpTransaction(Integer userId, BigDecimal amount, String pspRef, String description) {
        // Input validation
        if (userId == null) {
            throw new ValidationException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        if (pspRef == null || pspRef.trim().isEmpty()) {
            throw new ValidationException("PSP reference cannot be null or empty");
        }

        // Check if transaction with same pspRef already exists
        List<Transaction> existingTransactions = transactionRepository.findByPspRefAndStatus(pspRef, TransactionStatus.PENDING);
        if (!existingTransactions.isEmpty()) {
            throw new ValidationException("Transaction with PSP reference " + pspRef + " already exists");
        }

        Wallet wallet = walletRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for userId: " + userId));

        UUID groupId = generateGroupId();

        // Create system inflow transaction (for Master Wallet)
        Transaction systemInflow = Transaction.builder()
                .type(TransactionType.TOPUP)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.PSP)
                .actorUser(null)
                .systemWallet(SystemWallet.MASTER)
                .amount(amount)
                .currency("VND")
                .riderUser(null)
                .driverUser(null)
                .status(TransactionStatus.PENDING)
                .pspRef(pspRef)
                .note("PSP Inflow - " + description)
                .build();

        // Save system inflow transaction
        transactionRepository.save(systemInflow);

        // Create user credit transaction
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found for userId: " + userId));

        Transaction userCredit = Transaction.builder()
                .type(TransactionType.TOPUP)
                .groupId(groupId)
                .direction(TransactionDirection.IN)
                .actorKind(ActorKind.USER)
                .actorUser(user)
                .systemWallet(null)
                .amount(amount)
                .currency("VND")
                .riderUser(user)
                .pspRef(pspRef)
                .status(TransactionStatus.PENDING)
                .beforeAvail(wallet.getShadowBalance())
                .afterAvail(wallet.getShadowBalance())
                .beforePending(wallet.getPendingBalance())
                .afterPending(wallet.getPendingBalance().add(amount))
                .note(description)
                .build();

        // Save user credit transaction
        Transaction savedUserTransaction = transactionRepository.save(userCredit);

        // Update wallet pending balance
        walletService.increasePendingBalance(userId, amount);

        log.info("Created pending top-up transactions (system: {}, user: {}) for user {} with amount {} and pspRef {}",
                systemInflow.getTxnId(), savedUserTransaction.getTxnId(), userId, amount, pspRef);

        return savedUserTransaction;
    }

    @Override
    @Transactional
    public void completeTransaction(String pspRef) {
        // Input validation
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
        Wallet walletBefore = null;

        // Update all transactions to SUCCESS status
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.SUCCESS);

            // Get user info from user transaction (ActorKind.USER)
            if (txn.getActorKind() == ActorKind.USER && txn.getActorUser() != null) {
                userId = txn.getActorUser().getUserId();
                amount = txn.getAmount();
                userTxnId = txn.getTxnId();

                // Get wallet state before update
                Integer finalUserId = userId;
                walletBefore = walletRepository.findByUser_UserId(userId)
                        .orElseThrow(() -> new NotFoundException("Wallet not found for user: " + finalUserId));

                // Update transaction with final balances
                txn.setAfterAvail(walletBefore.getShadowBalance().add(amount));
                txn.setAfterPending(walletBefore.getPendingBalance().subtract(amount));
            }

            transactionRepository.save(txn);
        }

        // Transfer pending balance to available balance
        if (userId != null && amount != null) {
            walletService.transferPendingToAvailable(userId, amount);

            // Send success email
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null) {
                Wallet walletAfter = walletRepository.findByUser_UserId(userId).orElse(null);
                BigDecimal newBalance = walletAfter != null ? walletAfter.getShadowBalance() : BigDecimal.ZERO;

                emailService.sendTopUpSuccessEmail(
                        user.getEmail(),
                        user.getFullName(),
                        amount,
                        String.valueOf(userTxnId),
                        newBalance
                );

                log.info("Top-up success email sent to user {} for transaction {}", userId, userTxnId);
            }

            log.info("Completed transactions for pspRef {} - user {} with amount {}", pspRef, userId, amount);
        }
    }

    @Override
    @Transactional
    public void failTransaction(String pspRef, String reason) {
        // Input validation
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

        // Update all transactions to FAILED status
        for (Transaction txn : transactions) {
            txn.setStatus(TransactionStatus.FAILED);
            txn.setNote(txn.getNote() + " - Failed: " + reason);

            // Get user info from user transaction (ActorKind.USER)
            if (txn.getActorKind() == ActorKind.USER && txn.getActorUser() != null) {
                userId = txn.getActorUser().getUserId();
                amount = txn.getAmount();
                userTxnId = txn.getTxnId();
            }

            transactionRepository.save(txn);
        }

        // Decrease pending balance (since top-up failed, remove the pending amount)
        if (userId != null && amount != null) {
            walletService.decreasePendingBalance(userId, amount);

            // Send failed email
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getEmail() != null) {
                emailService.sendPaymentFailedEmail(
                        user.getEmail(),
                        user.getFullName(),
                        amount,
                        String.valueOf(userTxnId),
                        reason
                );

                log.info("Payment failed email sent to user {} for transaction {}", userId, userTxnId);
            }

            log.info("Failed transactions for pspRef {} - user {} with reason: {}", pspRef, userId, reason);
        }
    }

    @Override
    public Transaction getTransactionByPspRef(String pspRef) {
        return transactionRepository.findByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found for pspRef: " + pspRef));
    }

    public UUID generateGroupId() {
        return UUID.randomUUID();
    }
}
