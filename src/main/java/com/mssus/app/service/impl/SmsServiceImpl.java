package com.mssus.app.service.impl;

import com.mssus.app.service.SmsService;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsServiceImpl implements SmsService {
    @Value("${twilio.phone-number}")
    private String fromNumber;

    @Override
    public void sendSms(String to, String messageBody) {
        Message message = Message.creator(
            new PhoneNumber(to),
            new PhoneNumber(fromNumber),
            messageBody
        ).create();

        log.info("SMS sent to {} with SID: {}", to, message.getSid());
    }
}
