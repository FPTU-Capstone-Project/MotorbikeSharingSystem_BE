package org.kh.motorbikesharingsystem_be.service.impl;

import jakarta.annotation.PostConstruct;
import org.kh.motorbikesharingsystem_be.service.PayOSService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;

import java.util.concurrent.atomic.AtomicLong;

@Service
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
    public void init() {
        try {
            this.payOS = new PayOS(clientId, apiKey, checksumKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize PayOS client", e);
        }
    }


    @Override
    public CheckoutResponseData createPaymentLink(Long orderCode) throws Exception {
        ItemData itemData = ItemData.builder()
                .name("Mỳ tôm Hảo Hảo ly")
                .quantity(1)
                .price(2000)
                .build();
        PaymentData paymentData = PaymentData.builder()
                .orderCode(orderCode)
                .amount(2000)
                .description("Thanh toán đơn hàng")
                .buyerEmail("khavhuynhw@gmail.com")
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .item(itemData)
                .build();
        return payOS.createPaymentLink(paymentData);
    }


}
