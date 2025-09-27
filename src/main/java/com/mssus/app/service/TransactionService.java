package com.mssus.app.service;

import com.mssus.app.entity.Transactions;

import java.math.BigDecimal;

public interface TransactionService {
    Transactions createPendingTopUpTransaction(Integer userId, BigDecimal amount, String pspRef, String description);
    void completeTransaction(String pspRef);
    void failTransaction(String pspRef, String reason);
    Transactions getTransactionByPspRef(String pspRef);
}
