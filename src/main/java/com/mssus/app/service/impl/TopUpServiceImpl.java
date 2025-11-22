package com.mssus.app.service.impl;

import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.wallet.TopUpInitResponse;
import com.mssus.app.dto.response.wallet.TopUpWebhookConfirmResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import java.util.List;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.PayOSService;
import com.mssus.app.service.TopUpService;
import com.mssus.app.service.TransactionService;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.type.CheckoutResponseData;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopUpServiceImpl implements TopUpService {
    
    private final PayOSService payOSService;
    private final TransactionService transactionService;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    
    /**
     * Initiate top-up: Tạo PayOS payment link và PENDING transaction
     */
    @Override
    @Transactional
    public TopUpInitResponse initiateTopUp(TopUpInitRequest request, Authentication authentication) {
        if (authentication == null) {
            throw new ValidationException("Xác thực không được để trống");
        }
        
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với email: " + email));
        
        // Ensure wallet exists
        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId())
            .orElse(null);
        if (wallet == null) {
            wallet = walletService.createWalletForUser(user.getUserId());
        }
        
        // Validate wallet is active
        if (!wallet.getIsActive()) {
            throw new ValidationException("Ví đã bị đóng băng. Vui lòng liên hệ hỗ trợ.");
        }
        
        try {
            // 1. Tạo PayOS payment link
            String description = "Wallet top up";
            CheckoutResponseData paymentData = payOSService.createTopUpPaymentLink(
                user.getUserId(),
                request.getAmount(),
                    user.getEmail(),
                description,
                request.getReturnUrl(),
                request.getCancelUrl()
            );
            
            String orderCode = String.valueOf(paymentData.getOrderCode());
            
            // 2. ✅ FIX: Dùng TransactionService.initTopup() để tạo double-entry transactions
            // initTopup() tạo 2 transactions: System.MASTER OUT + User IN (balanced)
            List<Transaction> pendingTransactions = transactionService.initTopup(
                user.getUserId(),
                request.getAmount(),
                orderCode,  // pspRef
                description
            );
            
            log.info("Top-up initiated for user {} - amount: {}, orderCode: {}, transactions: {} (System OUT + User IN)",
                user.getUserId(), request.getAmount(), orderCode, pendingTransactions.size());
            
            return TopUpInitResponse.builder()
                .transactionRef(orderCode)
                .paymentUrl(paymentData.getCheckoutUrl())
                .qrCodeUrl(paymentData.getQrCode())
                .status("PENDING")
                .expirySeconds(900)
                .build();
                
        } catch (Exception e) {
            log.error("Error initiating top-up for user {}: {}", user.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Không thể bắt đầu nạp tiền: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle PayOS webhook callback
     * ✅ FIX: Dùng TransactionService.handleTopupSuccess/Failed() để update cả 2 transactions
     */
    @Override
    @Transactional
    public void handleTopUpWebhook(String orderCode, String status, BigDecimal amount) {
        // ✅ FIX: Dùng TransactionService methods để update cả 2 transactions (System + User)
        switch (status.toUpperCase()) {
            case "PAID":
            case "PROCESSING":
                // ✅ FIX: handleTopupSuccess() update cả System.MASTER OUT và User IN → SUCCESS
                transactionService.handleTopupSuccess(orderCode);
                log.info("Top-up completed for orderCode: {}", orderCode);
                break;
                
            case "CANCELLED":
            case "EXPIRED":
                // ✅ FIX: handleTopupFailed() update cả System.MASTER OUT và User IN → FAILED
                transactionService.handleTopupFailed(orderCode, "Payment " + status.toLowerCase());
                log.info("Top-up failed for orderCode: {}", orderCode);
                break;
                
            default:
                log.warn("Unknown payment status: {} for orderCode: {}", status, orderCode);
                break;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TopUpWebhookConfirmResponse confirmTopUpWebhook(String orderCode, BigDecimal amount) {
        // ✅ FIX: Tìm transaction bằng pspRef (orderCode)
        // initTopup() tạo 2 transactions với cùng pspRef, lấy user transaction
        String idempotencyKey = "TOPUP_" + orderCode + "_" + amount;
        Transaction txn = walletService.findTransactionByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new NotFoundException("Transaction not found for orderCode: " + orderCode));

        Long txnId = txn.getTxnId() == null ? null : txn.getTxnId().longValue();

        return TopUpWebhookConfirmResponse.builder()
                .orderCode(orderCode)
                .amount(amount)
                .transactionId(txnId)
                .transactionStatus(txn.getStatus() != null ? txn.getStatus().name() : "UNKNOWN")
                .message("Webhook already processed")
                .build();
    }
}

