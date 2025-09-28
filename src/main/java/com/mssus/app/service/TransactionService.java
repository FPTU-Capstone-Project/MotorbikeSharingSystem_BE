package com.mssus.app.service;

import com.mssus.app.entity.Transaction;

import java.math.BigDecimal;

public interface TransactionService {
    Transaction createPendingTopUpTransaction(Integer userId, BigDecimal amount, String pspRef, String description);
    void completeTransaction(String pspRef);
    void failTransaction(String pspRef, String reason);
    Transaction getTransactionByPspRef(String pspRef);
}
