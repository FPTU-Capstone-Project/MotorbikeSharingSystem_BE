package com.mssus.app.service.impl;

import com.mssus.app.entity.Transactions;
import com.mssus.app.repository.TransactionRepository;
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

    @Override
    @Transactional
    public Transactions createPendingTopUpTransaction(Integer userId, BigDecimal amount, String pspRef, String description) {
        Transactions transaction = new Transactions();
        transaction.setType("TOP_UP");
        transaction.setDirection("INBOUND");
        transaction.setActorKind("USER");
        transaction.setActorUserId(userId);
        transaction.setAmount(amount);
        transaction.setCurrency("VND");
        transaction.setPspRef(pspRef);
        transaction.setStatus("PENDING");
        transaction.setNote(description);

        Transactions savedTransaction = transactionRepository.save(transaction);

        walletService.increasePendingBalance(userId, amount);

        log.info("Created pending top-up transaction for user {} with amount {} and pspRef {}",
                userId, amount, pspRef);

        return savedTransaction;
    }

    @Override
    @Transactional
    public void completeTransaction(String pspRef) {
        Transactions transaction = transactionRepository.findPendingTransactionByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Pending transaction not found for pspRef: " + pspRef));

        BigDecimal amount = transaction.getAmount();
        Integer userId = transaction.getActorUserId();

        transaction.setStatus("COMPLETED");
        transactionRepository.save(transaction);

        walletService.transferPendingToAvailable(userId, amount);

        log.info("Completed transaction {} for user {} with amount {}", pspRef, userId, amount);
    }

    @Override
    @Transactional
    public void failTransaction(String pspRef, String reason) {
        Transactions transaction = transactionRepository.findPendingTransactionByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Pending transaction not found for pspRef: " + pspRef));

        BigDecimal amount = transaction.getAmount();
        Integer userId = transaction.getActorUserId();

        transaction.setStatus("FAILED");
        transaction.setNote(transaction.getNote() + " - Failed: " + reason);
        transactionRepository.save(transaction);

        walletService.decreasePendingBalance(userId, amount);

        log.info("Failed transaction {} for user {} with reason: {}", pspRef, userId, reason);
    }

    @Override
    public Transactions getTransactionByPspRef(String pspRef) {
        return transactionRepository.findByPspRef(pspRef)
                .orElseThrow(() -> new RuntimeException("Transaction not found for pspRef: " + pspRef));
    }
}
