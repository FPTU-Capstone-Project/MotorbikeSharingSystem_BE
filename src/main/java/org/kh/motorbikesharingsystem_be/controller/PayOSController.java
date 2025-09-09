package org.kh.motorbikesharingsystem_be.controller;

import org.kh.motorbikesharingsystem_be.service.PayOSService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.type.CheckoutResponseData;

import java.util.Map;

@RestController
@RequestMapping("/api/payos")
public class PayOSController {
    private final PayOSService payOSService;

    public PayOSController(PayOSService payOSService) {
        this.payOSService = payOSService;
    }

    @PostMapping("/create-payment-link")
    public ResponseEntity<?> createPaymentLink() {
        try {
            CheckoutResponseData response = payOSService.createPaymentLink(Long.valueOf("10001"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

}
