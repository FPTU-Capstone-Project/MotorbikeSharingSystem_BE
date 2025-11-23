package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.entity.Transaction;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.PayoutPollingService;
import com.mssus.app.service.PayoutNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutPollingServiceImpl implements PayoutPollingService {

    private final PayOSPayoutClient payOSPayoutClient;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculationService balanceCalculationService;
    private final PayoutNotificationService payoutNotificationService;

    @Value("${app.payout.polling.min-age-minutes:30}")
    private int minAgeMinutes;

    @Value("${app.payout.polling.max-age-hours:24}")
    private int maxAgeHours;

    @Override
    public boolean shouldPoll(Transaction transaction) {
        if (transaction.getType() != TransactionType.PAYOUT) {
            return false;
        }

        // Only poll PENDING or PROCESSING transactions
        if (transaction.getStatus() != TransactionStatus.PENDING && 
            transaction.getStatus() != TransactionStatus.PROCESSING) {
            return false;
        }

        // Only poll AUTOMATIC mode payouts
        String note = transaction.getNote();
        if (note == null || !note.contains("mode:AUTOMATIC")) {
            return false;
        }

        // Check age: must be older than minAgeMinutes but not older than maxAgeHours
        LocalDateTime createdAt = transaction.getCreatedAt();
        if (createdAt == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        long ageMinutes = java.time.Duration.between(createdAt, now).toMinutes();
        
        if (ageMinutes < minAgeMinutes) {
            return false; // Too new, webhook might still come
        }

        if (ageMinutes > maxAgeHours * 60) {
            return false; // Too old, probably failed
        }

        return true;
    }

    @Override
    @Transactional
    public boolean pollPayoutStatus(Transaction transaction) {
        String payoutRef = transaction.getPspRef();
        if (payoutRef == null) {
            log.warn("Cannot poll payout status: transaction {} has no pspRef", transaction.getTxnId());
            return false;
        }

        try {
            log.info("Polling PayOS status for payout ref={}, txnId={}", payoutRef, transaction.getTxnId());

            // Call PayOS API to get status
            JsonNode payosResponse = payOSPayoutClient.getPayoutStatusByRef(payoutRef);

            // Parse response
            String payosCode = payosResponse.path("code").asText();
            String payosDesc = payosResponse.path("desc").asText();
            JsonNode dataNode = payosResponse.path("data");
            String payosStatus = dataNode.path("status").asText("").toUpperCase();
            String payosTransactionId = dataNode.path("transactionId").asText("");

            log.debug("PayOS status response for ref={}: code={}, status={}, txnId={}", 
                    payoutRef, payosCode, payosStatus, payosTransactionId);

            // Find all transactions in the group
            List<Transaction> transactions = transactionRepository.findByGroupId(transaction.getGroupId());
            Transaction userTransaction = transactions.stream()
                    .filter(txn -> txn.getActorUser() != null && txn.getType() == TransactionType.PAYOUT)
                    .findFirst()
                    .orElse(transaction);

            boolean statusChanged = false;
            TransactionStatus newStatus = null;

            // Determine new status based on PayOS response
            if ("SUCCESS".equals(payosStatus) || "COMPLETED".equals(payosStatus) || "00".equals(payosCode)) {
                newStatus = TransactionStatus.SUCCESS;
            } else if ("FAILED".equals(payosStatus) || "FAILURE".equals(payosStatus)) {
                newStatus = TransactionStatus.FAILED;
            } else if ("PROCESSING".equals(payosStatus) || "PENDING".equals(payosStatus)) {
                newStatus = TransactionStatus.PROCESSING;
            }

            // Update transactions if status changed
            if (newStatus != null && newStatus != transaction.getStatus()) {
                statusChanged = true;
                
                for (Transaction txn : transactions) {
                    txn.setStatus(newStatus);
                    
                    String note = txn.getNote() != null ? txn.getNote() : "";
                    note += String.format(" | polled_at:%s | payos_code:%s | payos_status:%s | payos_txn_id:%s",
                            LocalDateTime.now(),
                            payosCode,
                            payosStatus,
                            payosTransactionId);
                    txn.setNote(note);
                    
                    transactionRepository.save(txn);
                }

                // Invalidate balance cache
                if (userTransaction.getWallet() != null) {
                    balanceCalculationService.invalidateBalanceCache(userTransaction.getWallet().getWalletId());
                }

                // Send notification
                if (userTransaction.getActorUser() != null) {
                    if (newStatus == TransactionStatus.SUCCESS) {
                        payoutNotificationService.notifyPayoutSuccess(
                                userTransaction.getActorUser(),
                                payoutRef,
                                userTransaction.getAmount());
                    } else if (newStatus == TransactionStatus.FAILED) {
                        payoutNotificationService.notifyPayoutFailed(
                                userTransaction.getActorUser(),
                                payoutRef,
                                userTransaction.getAmount(),
                                payosDesc);
                    }
                }

                log.info("Payout status updated via polling: ref={}, oldStatus={}, newStatus={}",
                        payoutRef, transaction.getStatus(), newStatus);
            } else {
                log.debug("Payout status unchanged via polling: ref={}, status={}",
                        payoutRef, transaction.getStatus());
            }

            return statusChanged;

        } catch (Exception ex) {
            log.error("Failed to poll PayOS status for payout ref={}: {}", payoutRef, ex.getMessage(), ex);
            return false;
        }
    }
}

