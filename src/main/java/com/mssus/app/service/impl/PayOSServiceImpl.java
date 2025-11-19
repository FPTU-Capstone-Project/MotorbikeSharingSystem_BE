package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.service.PayOSService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.PaymentData;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSServiceImpl implements PayOSService {

    private final ObjectMapper objectMapper;
    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

//    @Value("${payos.return-url}")
//    private String returnUrl;
//
//    @Value("${payos.cancel-url}")
//    private String cancelUrl;

    private PayOS payOS;
    private static final AtomicLong orderCodeCounter = new AtomicLong(System.currentTimeMillis() / 1000);
    private static final long expiredAt = (System.currentTimeMillis() / 1000) + 15 * 60;

    @PostConstruct
    public void init() {
        try {
            this.payOS = new PayOS(clientId, apiKey, checksumKey);
            log.info("PayOS service initialized with client ID: {}", clientId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PayOS client", e);
        }
    }

    public Long generateUniqueOrderCode() {
        return orderCodeCounter.incrementAndGet();
    }

    /**
     * ✅ Chỉ tạo PayOS payment link, KHÔNG tạo transaction
     */
    @Override
    public CheckoutResponseData createTopUpPaymentLink(
            Integer userId, 
            BigDecimal amount,
            String email,
            @Nonnull String description, 
            String returnUrl, 
            String cancelUrl) throws Exception {
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        
        Long orderCode = generateUniqueOrderCode();
        
        PaymentData data = PaymentData.builder()
            .orderCode(orderCode)
            .amount(amount.intValue())
            .description(description)
            .returnUrl(returnUrl)
            .cancelUrl(cancelUrl)
                .expiredAt(expiredAt)
            .buyerEmail(email)
            .build();
        
        CheckoutResponseData response = payOS.createPaymentLink(data);
        
        log.info("Created PayOS payment link for user {} with amount {} and orderCode {}",
            userId, amount, orderCode);
        
        return response;
    }

    /**
     * ✅ Parse webhook payload, KHÔNG xử lý transaction
     */
    @Override
    public PayOSService.WebhookPayload parseWebhook(String payload) {
        try {
            Webhook webhook = objectMapper.readValue(payload, Webhook.class);

            WebhookData data = payOS.verifyPaymentWebhookData(webhook);

            Long orderCode = data.getOrderCode();
            Integer amount = data.getAmount();
            String status = "00".equals(data.getCode()) ? "PAID" : "FAILED";

            return new PayOSService.WebhookPayload(
                    orderCode != null ? orderCode.toString() : null,
                    status,
                    amount != null ? BigDecimal.valueOf(amount) : BigDecimal.ZERO
            );
        } catch (Exception e) {
            log.error("Error parsing webhook payload: {}", payload, e);
            throw new RuntimeException("Error parsing webhook", e);
        }
    }

}
