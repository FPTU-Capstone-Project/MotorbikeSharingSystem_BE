package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.enums.TransactionType;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.wallet.PayoutWebhookRequest;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.TransactionRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.BalanceCalculationService;
import com.mssus.app.service.PayoutNotificationService;
import com.mssus.app.service.PayoutWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutWebhookServiceImpl implements PayoutWebhookService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final BalanceCalculationService balanceCalculationService;
    private final PayoutNotificationService payoutNotificationService;
    private final ObjectMapper objectMapper;

    @Value("${payos.payout.checksum-key}")
    private String payoutChecksumKey;

    @Override
    @Transactional
    public void handlePayoutWebhook(PayoutWebhookRequest webhookRequest, String rawPayload) {
        // 1. Verify webhook signature
        if (!verifyWebhookSignature(rawPayload, webhookRequest.getSignature())) {
            log.error("Invalid webhook signature for referenceId={}", webhookRequest.getReferenceId());
            throw new ValidationException("Invalid webhook signature");
        }

        // 2. Find transaction by referenceId (pspRef)
        // Try different statuses to find the transaction group
        List<Transaction> transactions = transactionRepository.findByPspRefAndStatus(
                webhookRequest.getReferenceId(), 
                TransactionStatus.PENDING);
        
        if (transactions.isEmpty()) {
            transactions = transactionRepository.findByPspRefAndStatus(
                    webhookRequest.getReferenceId(), 
                    TransactionStatus.PROCESSING);
        }
        
        if (transactions.isEmpty()) {
            transactions = transactionRepository.findByPspRefAndStatus(
                    webhookRequest.getReferenceId(), 
                    TransactionStatus.SUCCESS);
        }
        
        if (transactions.isEmpty()) {
            transactions = transactionRepository.findByPspRefAndStatus(
                    webhookRequest.getReferenceId(), 
                    TransactionStatus.FAILED);
        }
        
        // Last resort: try findByPspRef (returns single transaction, but we need group)
        if (transactions.isEmpty()) {
            Optional<Transaction> singleTxn = transactionRepository.findByPspRef(webhookRequest.getReferenceId());
            if (singleTxn.isPresent()) {
                // Find all transactions in the same group
                transactions = transactionRepository.findByGroupId(singleTxn.get().getGroupId());
            }
        }
        
        if (transactions.isEmpty()) {
            log.warn("No transaction found for webhook referenceId={}", webhookRequest.getReferenceId());
            throw new NotFoundException("Transaction not found for referenceId: " + webhookRequest.getReferenceId());
        }

        Transaction userTransaction = transactions.stream()
                .filter(txn -> txn.getType() == TransactionType.PAYOUT && txn.getActorUser() != null)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("User payout transaction not found for referenceId: " + webhookRequest.getReferenceId()));

        // 3. Idempotency check: ignore duplicate webhooks
        String payloadHash = calculatePayloadHash(rawPayload);
        if (userTransaction.getNote() != null && userTransaction.getNote().contains("webhook_hash:" + payloadHash)) {
            log.info("Duplicate webhook ignored for referenceId={}, hash={}", webhookRequest.getReferenceId(), payloadHash);
            return; // Idempotent - already processed
        }

        // 4. Update transaction status based on webhook status
        String status = webhookRequest.getStatus() != null ? webhookRequest.getStatus().toUpperCase() : "";
        
        switch (status) {
            case "SUCCESS":
            case "COMPLETED":
                updateTransactionToSuccess(userTransaction, transactions, webhookRequest, payloadHash);
                break;
            case "FAILED":
            case "FAILURE":
                updateTransactionToFailed(userTransaction, transactions, webhookRequest, payloadHash);
                break;
            case "PROCESSING":
                updateTransactionToProcessing(userTransaction, transactions, webhookRequest, payloadHash);
                break;
            default:
                log.warn("Unknown webhook status: {} for referenceId={}", status, webhookRequest.getReferenceId());
                break;
        }

        log.info("Webhook processed for referenceId={}, status={}", webhookRequest.getReferenceId(), status);
    }

    private void updateTransactionToSuccess(Transaction userTransaction, List<Transaction> allTransactions,
                                           PayoutWebhookRequest webhookRequest, String payloadHash) {
        // Update all transactions in group to SUCCESS
        for (Transaction txn : allTransactions) {
            if (txn.getStatus() == TransactionStatus.SUCCESS) {
                log.debug("Transaction already SUCCESS for referenceId={}", webhookRequest.getReferenceId());
                continue; // Already processed
            }
            txn.setStatus(TransactionStatus.SUCCESS);
            
            // Store webhook metadata in note
            String note = txn.getNote() != null ? txn.getNote() : "";
            note += String.format(" | webhook_hash:%s | payos_txn_id:%s | completed_at:%s",
                    payloadHash,
                    webhookRequest.getTransactionId() != null ? webhookRequest.getTransactionId() : "",
                    webhookRequest.getCompletedAt() != null ? webhookRequest.getCompletedAt() : LocalDateTime.now());
            txn.setNote(note);
            
            transactionRepository.save(txn);
        }

        // Invalidate balance cache
        if (userTransaction.getWallet() != null) {
            balanceCalculationService.invalidateBalanceCache(userTransaction.getWallet().getWalletId());
        }

        // Send success notification
        if (userTransaction.getActorUser() != null) {
            payoutNotificationService.notifyPayoutSuccess(
                    userTransaction.getActorUser(),
                    webhookRequest.getReferenceId(),
                    userTransaction.getAmount());
        }

        log.info("Payout marked SUCCESS for referenceId={}, payosTransactionId={}",
                webhookRequest.getReferenceId(), webhookRequest.getTransactionId());
    }

    private void updateTransactionToFailed(Transaction userTransaction, List<Transaction> allTransactions,
                                          PayoutWebhookRequest webhookRequest, String payloadHash) {
        // Update all transactions to FAILED
        for (Transaction txn : allTransactions) {
            if (txn.getStatus() == TransactionStatus.FAILED) {
                log.debug("Transaction already FAILED for referenceId={}", webhookRequest.getReferenceId());
                continue; // Already processed
            }
            txn.setStatus(TransactionStatus.FAILED);
            
            String note = txn.getNote() != null ? txn.getNote() : "";
            note += String.format(" | webhook_hash:%s | payos_txn_id:%s | failure_reason:%s",
                    payloadHash,
                    webhookRequest.getTransactionId() != null ? webhookRequest.getTransactionId() : "",
                    webhookRequest.getFailureReason() != null ? webhookRequest.getFailureReason() : "Unknown");
            txn.setNote(note);
            
            transactionRepository.save(txn);
        }

        // Create refund transaction if needed
        if (userTransaction.getWallet() != null && userTransaction.getActorUser() != null) {
            createRefundForFailedPayout(userTransaction, webhookRequest.getFailureReason());
        }

        // Invalidate balance cache
        if (userTransaction.getWallet() != null) {
            balanceCalculationService.invalidateBalanceCache(userTransaction.getWallet().getWalletId());
        }

        // Send failed notification
        if (userTransaction.getActorUser() != null) {
            payoutNotificationService.notifyPayoutFailed(
                    userTransaction.getActorUser(),
                    webhookRequest.getReferenceId(),
                    userTransaction.getAmount(),
                    webhookRequest.getFailureReason());
        }

        log.info("Payout marked FAILED for referenceId={}, reason={}",
                webhookRequest.getReferenceId(), webhookRequest.getFailureReason());
    }

    private void updateTransactionToProcessing(Transaction userTransaction, List<Transaction> allTransactions,
                                              PayoutWebhookRequest webhookRequest, String payloadHash) {
        // Update to PROCESSING if not already SUCCESS or FAILED
        for (Transaction txn : allTransactions) {
            if (txn.getStatus() == TransactionStatus.SUCCESS || txn.getStatus() == TransactionStatus.FAILED) {
                continue; // Don't downgrade from final states
            }
            txn.setStatus(TransactionStatus.PROCESSING);
            
            String note = txn.getNote() != null ? txn.getNote() : "";
            note += String.format(" | webhook_hash:%s | payos_txn_id:%s",
                    payloadHash,
                    webhookRequest.getTransactionId() != null ? webhookRequest.getTransactionId() : "");
            txn.setNote(note);
            
            transactionRepository.save(txn);
        }

        log.info("Payout marked PROCESSING for referenceId={}", webhookRequest.getReferenceId());
    }

    private void createRefundForFailedPayout(Transaction failedPayout, String reason) {
        User user = failedPayout.getActorUser();
        Wallet wallet = failedPayout.getWallet();
        BigDecimal amount = failedPayout.getAmount();

        UUID refundGroupId = UUID.randomUUID();
        Transaction refundTxn = Transaction.builder()
                .groupId(refundGroupId)
                .wallet(wallet)
                .type(TransactionType.REFUND)
                .direction(com.mssus.app.common.enums.TransactionDirection.IN)
                .actorKind(com.mssus.app.common.enums.ActorKind.SYSTEM)
                .actorUser(user)
                .amount(amount)
                .currency("VND")
                .status(TransactionStatus.SUCCESS)
                .note("Refund for failed PayOS payout: " + failedPayout.getPspRef() + " | reason: " + reason)
                .build();

        transactionRepository.save(refundTxn);
        log.info("Refund transaction created for failed payout referenceId={}, amount={}",
                failedPayout.getPspRef(), amount);
    }

    /**
     * Verify webhook signature using HMAC-SHA256.
     */
    private boolean verifyWebhookSignature(String payload, String receivedSignature) {
        if (receivedSignature == null || receivedSignature.isEmpty()) {
            log.warn("Webhook signature is missing");
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(payoutChecksumKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(rawHmac);

            boolean isValid = calculatedSignature.equalsIgnoreCase(receivedSignature);
            if (!isValid) {
                log.warn("Webhook signature mismatch. Calculated: {}, Received: {}", calculatedSignature, receivedSignature);
            }
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    /**
     * Calculate hash of payload for idempotency check.
     */
    private String calculatePayloadHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            log.error("Error calculating payload hash", e);
            return payload.hashCode() + ""; // Fallback
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}

