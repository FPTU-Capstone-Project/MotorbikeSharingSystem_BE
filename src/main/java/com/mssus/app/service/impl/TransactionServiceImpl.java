package com.mssus.app.service.impl;

import com.mssus.app.common.enums.ActorKind;
import com.mssus.app.common.enums.TransactionDirection;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
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
        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.TOPUP);
        transaction.setDirection(TransactionDirection.IN);
        transaction.setActorKind(ActorKind.USER);
        transaction.setActorUser(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found")));
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setPspRef(pspRef);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setNote(description);

        Transaction savedTransaction = transactionRepository.save(transaction);

        walletService.increasePendingBalance(userId, amount);

        log.info("Created pending top-up transaction for user {} with amount {} and pspRef {}",
                userId, amount, pspRef);

        return savedTransaction;
    }

    @Override
    @Transactional
    public void completeTransaction(String pspRef) {
        Transaction transaction = transactionRepository.findPendingTransactionByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Pending transaction not found for pspRef: " + pspRef));

        BigDecimal amount = transaction.getAmount();
        Integer userId = transaction.getActorUser().getUserId();

        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        walletService.transferPendingToAvailable(userId, amount);

        // Send success email
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getEmail() != null) {
            Wallet wallet = walletRepository.findByUser_UserId(userId).orElse(null);
            BigDecimal newBalance = wallet != null ? wallet.getShadowBalance() : BigDecimal.ZERO;

            emailService.sendTopUpSuccessEmail(
                    user.getEmail(),
                    user.getFullName(),
                    amount,
                    String.valueOf(transaction.getTxnId()),
                    newBalance
            );

            log.info("Top-up success email sent to user {} for transaction {}", userId, transaction.getTxnId());
        }

        log.info("Completed transaction {} for user {} with amount {}", pspRef, userId, amount);
    }

    @Override
    @Transactional
    public void failTransaction(String pspRef, String reason) {
        Transaction transaction = transactionRepository.findPendingTransactionByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Pending transaction not found for pspRef: " + pspRef));

        BigDecimal amount = transaction.getAmount();
        Integer userId = transaction.getActorUser().getUserId();

        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setNote(transaction.getNote() + " - Failed: " + reason);
        transactionRepository.save(transaction);

        walletService.decreasePendingBalance(userId, amount);

        // Send failed email
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getEmail() != null) {
            emailService.sendPaymentFailedEmail(
                    user.getEmail(),
                    user.getFullName(),
                    amount,
                    String.valueOf(transaction.getTxnId()),
                    reason
            );

            log.info("Payment failed email sent to user {} for transaction {}", userId, transaction.getTxnId());
        }

        log.info("Failed transaction {} for user {} with reason: {}", pspRef, userId, reason);
    }

    @Override
    public Transaction getTransactionByPspRef(String pspRef) {
        return transactionRepository.findByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found for pspRef: " + pspRef));
    }
}
