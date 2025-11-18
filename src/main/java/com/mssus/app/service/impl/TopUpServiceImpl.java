package com.mssus.app.service.impl;

import com.mssus.app.common.enums.TransactionStatus;
import com.mssus.app.common.exception.NotFoundException;
import com.mssus.app.common.exception.ValidationException;
import com.mssus.app.dto.request.wallet.TopUpInitRequest;
import com.mssus.app.dto.response.wallet.TopUpInitResponse;
import com.mssus.app.dto.response.wallet.TopUpWebhookConfirmResponse;
import com.mssus.app.entity.Transaction;
import com.mssus.app.entity.User;
import com.mssus.app.entity.Wallet;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.WalletRepository;
import com.mssus.app.service.PayOSService;
import com.mssus.app.service.TopUpService;
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
    private final WalletService walletService;  // ✅ SSOT service
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
            
            // 2. ✅ SSOT: Tạo PENDING transaction
            // Generate idempotency key từ orderCode
            String idempotencyKey = generateIdempotencyKey(orderCode, request.getAmount());
            
            Transaction pendingTxn = walletService.createTopUpTransaction(
                user.getUserId(),
                request.getAmount(),
                orderCode,  // pspRef
                idempotencyKey,
                TransactionStatus.PENDING  // ✅ PENDING until webhook confirms
            );
            
            log.info("Top-up initiated for user {} - amount: {}, orderCode: {}, txnId: {}",
                user.getUserId(), request.getAmount(), orderCode, pendingTxn.getTxnId());
            
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
     */
    @Override
    @Transactional
    public void handleTopUpWebhook(String orderCode, String status, BigDecimal amount) {
        // 1. Generate idempotency key (same as in initiateTopUp)
        String idempotencyKey = generateIdempotencyKey(orderCode, amount);
        
        // 2. Find existing transaction by idempotency key
        Transaction existingTxn = walletService.findTransactionByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> new NotFoundException(
                "Transaction not found for orderCode: " + orderCode));
        
        // 3. Update transaction status based on webhook status
        switch (status.toUpperCase()) {
            case "PAID":
            case "PROCESSING":
                walletService.completeTopUpTransaction(existingTxn.getTxnId());
                log.info("Top-up completed for orderCode: {}, txnId: {}", 
                    orderCode, existingTxn.getTxnId());
                break;
                
            case "CANCELLED":
            case "EXPIRED":
                walletService.failTopUpTransaction(existingTxn.getTxnId(), 
                    "Payment " + status.toLowerCase());
                log.info("Top-up failed for orderCode: {}, txnId: {}", 
                    orderCode, existingTxn.getTxnId());
                break;
                
            default:
                log.warn("Unknown payment status: {} for orderCode: {}", status, orderCode);
                break;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TopUpWebhookConfirmResponse confirmTopUpWebhook(String orderCode, BigDecimal amount) {
        String idempotencyKey = generateIdempotencyKey(orderCode, amount);
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
    
    private String generateIdempotencyKey(String orderCode, BigDecimal amount) {
        // Unique key từ orderCode + amount
        return String.format("TOPUP_%s_%s", orderCode, amount);
    }
}

