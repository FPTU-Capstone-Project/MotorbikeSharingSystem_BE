package com.mssus.app.EventHandler;

import com.mssus.app.BaseEvent.EmailChangedEvent;
import com.mssus.app.BaseEvent.PhoneChangedEvent;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProfileUpdateEventHandler {
    private final EmailService emailService;
    private final SmsService smsService;

    @EventListener
    @Async
    public void handleEmailChanged(EmailChangedEvent event) {
        log.info("Email changed for user {}: {} -> {}",
                event.getUserId(), event.getOldEmail(), event.getNewEmail());

        try {
            emailService.sendVerificationEmail(event.getNewEmail(), Long.valueOf(event.getUserId()));
        } catch (Exception e) {
            log.error("Failed to send verification email", e);
        }
    }

    @EventListener
    @Async
    public void handlePhoneChanged(PhoneChangedEvent event) {
        log.info("Phone changed for user {}: {} -> {}",
                event.getUserId(), event.getOldPhone(), event.getNewPhone());

        try {
            smsService.sendVerificationSms(event.getNewPhone(), Long.valueOf(event.getUserId()));
        } catch (Exception e) {
            log.error("Failed to send verification SMS", e);
        }
    }
}
