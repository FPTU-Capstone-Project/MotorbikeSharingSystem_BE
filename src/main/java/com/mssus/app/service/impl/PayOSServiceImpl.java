package com.mssus.app.service.impl;

import com.mssus.app.service.PayOSService;
import com.twilio.rest.api.v2010.account.call.PaymentCreator;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.PaymentData;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOSServiceImpl implements PayOSService {
    @Value("${payos.client-id}")
    private String clientId;

    @Value("${payos.api-key}")
    private String apiKey;

    @Value("${payos.checksum-key}")
    private String checksumKey;

    @Value("${payos.return-url}")
    private String returnUrl;

    @Value("${payos.cancel-url}")
    private String cancelUrl;

    private PayOS payOS;
    private static final AtomicLong orderCodeCounter = new AtomicLong(System.currentTimeMillis() / 1000);

    @PostConstruct
    public void init(){
        try {
            this.payOS = new PayOS(clientId, apiKey, checksumKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PayOS client", e);
        }
    }


    @Override
    public CheckoutResponseData createPaymentLink(Long orderCode, BigDecimal amount, @Nonnull String description) throws Exception {
        try{
            if (orderCode == null) {
                orderCode = generateUniqueOrderCode();
            }
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
            return payOS.createPaymentLink(data);
        }catch (Exception e){
            log.error("Error creating payment link: {}", orderCode, e);
            throw new RuntimeException("Error creating payment link for orderCode: " + orderCode, e);
        }
    }

    public Long generateUniqueOrderCode() {
        return orderCodeCounter.incrementAndGet();
    }

}
