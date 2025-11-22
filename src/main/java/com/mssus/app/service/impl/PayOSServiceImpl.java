package com.mssus.app.service.impl;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSServiceImpl implements PayOSService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;
    
    private static final String PAYOS_API_BASE_URL = "https://api-merchant.payos.vn";

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

    @Override
    public void cancelPaymentLink(Long orderCode, String cancellationReason) throws Exception {
        if (orderCode == null) {
            throw new IllegalArgumentException("OrderCode cannot be null");
        }
        
        try {
            String url = PAYOS_API_BASE_URL + "/v2/payment-requests/" + orderCode + "/cancel";
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-client-id", clientId);
            headers.set("x-api-key", apiKey);
            
            // Set request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("cancellationReason", cancellationReason != null ? cancellationReason : "Recreating payment link");
            
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            
            // Call API
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Cancelled PayOS payment link for orderCode: {}, reason: {}", orderCode, cancellationReason);
            } else {
                log.warn("Cancel payment link returned non-2xx status: {} for orderCode: {}", 
                    response.getStatusCode(), orderCode);
            }
            
        } catch (Exception e) {
            log.error("Error cancelling PayOS payment link for orderCode {}: {}", orderCode, e.getMessage(), e);
            // ✅ Không throw exception nếu cancel fail (có thể link đã expired hoặc không tồn tại)
            // Chỉ log warning và tiếp tục tạo lại link
            log.warn("Failed to cancel payment link, will proceed to recreate: {}", e.getMessage());
        }
    }
    
    /**
     * ✅ Tạo lại PayOS payment link với orderCode mới (dùng khi duplicate request)
     * PayOS không cho phép tạo trùng orderCode, nên phải:
     * 1. Hủy link cũ
     * 2. Tạo orderCode mới
     * 3. Tạo payment link mới với orderCode mới
     */
    @Override
    public CheckoutResponseData recreateTopUpPaymentLink(
            Long oldOrderCode,
            Integer userId,
            BigDecimal amount,
            String email,
            @Nonnull String description,
            String returnUrl,
            String cancelUrl) throws Exception {
        
        if (oldOrderCode == null) {
            throw new IllegalArgumentException("Old orderCode cannot be null");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        
        // ✅ Hủy link thanh toán cũ trước khi tạo lại
        try {
            cancelPaymentLink(oldOrderCode, "Recreating payment link for duplicate request");
        } catch (Exception e) {
            // Log warning nhưng tiếp tục tạo link mới
            log.warn("Failed to cancel existing payment link for orderCode {}, will proceed to create new link: {}", 
                oldOrderCode, e.getMessage());
        }
        
        // ✅ Tạo orderCode mới (PayOS không cho phép tạo trùng orderCode)
        Long newOrderCode = generateUniqueOrderCode();
        
        // ✅ Tạo payment link mới với orderCode mới
        PaymentData data = PaymentData.builder()
            .orderCode(newOrderCode)  // ✅ Dùng orderCode mới
            .amount(amount.intValue())
            .description(description)
            .returnUrl(returnUrl)
            .cancelUrl(cancelUrl)
            .expiredAt(expiredAt)  // ✅ Reset expiry time
            .buyerEmail(email)
            .build();
        
        CheckoutResponseData response = payOS.createPaymentLink(data);
        
        log.info("Recreated PayOS payment link for user {} with amount {}. Old orderCode: {}, new orderCode: {}",
            userId, amount, oldOrderCode, newOrderCode);
        
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
