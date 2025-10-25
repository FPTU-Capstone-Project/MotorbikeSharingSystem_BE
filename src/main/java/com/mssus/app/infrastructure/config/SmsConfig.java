package com.mssus.app.infrastructure.config;

import com.twilio.Twilio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
@Slf4j
public class SmsConfig {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @PostConstruct
    public void initTwilio() {
        Twilio.init(accountSid, authToken);
        String maskedSid = accountSid.substring(0, 4) + "****" + accountSid.substring(accountSid.length() - 4);
        log.info("Twilio initialized with Account SID: {}", maskedSid);
    }
}

