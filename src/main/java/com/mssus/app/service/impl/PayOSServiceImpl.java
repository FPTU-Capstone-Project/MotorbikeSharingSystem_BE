package com.mssus.app.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mssus.app.service.PayOSService;
import com.mssus.app.service.TransactionService;
import com.twilio.rest.api.v2010.account.call.PaymentCreator;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.PaymentData;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class PayOSServiceImpl implements PayOSService {

    private final TransactionService transactionService;
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

    public PayOSServiceImpl(@Lazy TransactionService transactionService, ObjectMapper objectMapper) {
       this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            this.payOS = new PayOS(clientId, apiKey, checksumKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PayOS client", e);
        }
    }

    public Long generateUniqueOrderCode() {
        return orderCodeCounter.incrementAndGet();
    }

    @Override
    public CheckoutResponseData createTopUpPaymentLink(Integer userId, BigDecimal amount, @Nonnull String description, String returnUrl, String cancelUrl) throws Exception {
        try {
            Long orderCode = generateUniqueOrderCode();

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }

            PaymentData data = PaymentData.builder()
                    .orderCode(orderCode)
                    .amount(amount.intValue())
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CheckoutResponseData response = payOS.createPaymentLink(data);

//            transactionService.initTopup(userId, amount, orderCode.toString(), description);

            log.info("Created top-up payment link for user {} with amount {} and orderCode {}",
                    userId, amount, orderCode);

            return response;
        } catch (Exception e) {
            log.error("Error creating top-up payment link for user: {}", userId, e);
            throw new RuntimeException("Error creating top-up payment link for user: " + userId, e);
        }
    }

    @Override
    public void handleWebhook(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            JsonNode data = jsonNode.get("data");

            if (data != null) {
                String orderCode = data.get("orderCode").asText();
                String status = data.get("status").asText();
                String description = data.has("description") ? data.get("description").asText() : "";

                log.info("Processing webhook for orderCode: {} with status: {}", orderCode, status);

                switch (status.toUpperCase()) {
                    case "PAID":
                    case "PROCESSING":
//                        transactionService.handleTopupSuccess(orderCode);
                        log.info("Transaction completed for orderCode: {}", orderCode);
                        break;
                    case "CANCELLED":
                    case "EXPIRED":
//                        transactionService.handleTopupFailed(orderCode, "Payment " + status.toLowerCase());
                        log.info("Transaction failed for orderCode: {} with reason: {}", orderCode, status);
                        break;
                    default:
                        log.warn("Unknown payment status: {} for orderCode: {}", status, orderCode);
                        break;
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", payload, e);
            throw new RuntimeException("Error processing webhook", e);
        }
    }

}
