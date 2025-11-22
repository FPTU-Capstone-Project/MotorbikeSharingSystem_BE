package com.mssus.app.service.impl;

import com.mssus.app.common.enums.OtpFor;
import com.mssus.app.common.enums.PaymentMethod;
import com.mssus.app.common.enums.UserStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.appconfig.config.properties.SosConfigurationProperties;
import com.mssus.app.dto.request.GetOtpRequest;
import com.mssus.app.dto.request.OtpRequest;
import com.mssus.app.dto.response.OtpResponse;
import com.mssus.app.dto.response.notification.EmailPriority;
import com.mssus.app.common.enums.RiderProfileStatus;
import com.mssus.app.entity.RiderProfile;
import com.mssus.app.entity.User;
import com.mssus.app.repository.UserRepository;
import com.mssus.app.repository.RiderProfileRepository;
import com.mssus.app.service.EmailService;
import com.mssus.app.service.EmergencyContactService;
import com.mssus.app.service.OtpService;
import com.mssus.app.common.util.OtpUtil;
import com.mssus.app.dto.domain.sos.EmergencyContactRequest;
import com.mssus.app.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {
    private final UserRepository userRepository;
    private final RiderProfileRepository riderProfileRepository;
    private final EmailService emailService;
    private final EmergencyContactService emergencyContactService;
    private final SosConfigurationProperties sosConfig;
    private final WalletService walletService;

    @Override
    public OtpResponse requestOtp(GetOtpRequest request) {
        OtpFor otpFor = OtpFor.valueOf(request.getOtpFor());
        String email = request.getEmail();

        User user = switch (otpFor) {
            case VERIFY_EMAIL -> userRepository.findByEmailAndStatus(email, UserStatus.EMAIL_VERIFYING)
                .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email", "Người dùng với email không ở trạng thái xác thực: " + email));
            case VERIFY_PHONE, FORGOT_PASSWORD -> userRepository.findByEmailAndStatusNot(email, UserStatus.EMAIL_VERIFYING)
                .orElseThrow(() -> BaseDomainException.formatted("user.not-found.by-email", "Không tìm thấy người dùng với email: %s", email));
        };

        String otp = OtpUtil.generateOtp();
        String otpKey = user.getEmail() + ":" + otpFor;
        OtpUtil.storeOtp(otpKey, otp, otpFor);

        String subject = switch (otpFor) {
            case VERIFY_EMAIL -> "Verify your email";
            case VERIFY_PHONE -> "Verify your phone";
            case FORGOT_PASSWORD -> "Reset your password";
        };

        String templateName = switch (otpFor) {
            case VERIFY_EMAIL -> "emails/otp-email-verification";
            case VERIFY_PHONE -> "emails/otp-phone-verification";
            case FORGOT_PASSWORD -> "emails/otp-password-reset";
        };

        Map<String, Object> templateVars = switch (otpFor) {
            case VERIFY_EMAIL, FORGOT_PASSWORD, VERIFY_PHONE -> Map.of("fullName", user.getFullName(), "otpCode", otp );
        };

        EmailPriority priority = EmailPriority.HIGH;

        String emailType = switch (otpFor) {
            case VERIFY_EMAIL -> "email-verification";
            case VERIFY_PHONE -> "phone-verification";
            case FORGOT_PASSWORD -> "password-reset";
        };

        emailService.sendEmail(email,
            subject,
            templateName,
            templateVars,
            priority,
            Long.valueOf(user.getUserId()),
            emailType);

        log.info("OTP generated for {}: {} (dev mode)", otpFor, otp);

        return OtpResponse.builder()
            .message("Đã gửi OTP thành công")
            .otpFor(otpFor.name())
            .build();
    }

    @Override
    @Transactional
    public OtpResponse verifyOtp(OtpRequest request) {
        // Find user context from OTP

        String key = request.getEmail() + ":" + request.getOtpFor();

        if (!OtpUtil.validateOtp(key, request.getCode(), OtpFor.valueOf(request.getOtpFor()))) {
            throw BaseDomainException.of("otp.validation.invalid-otp", "OTP không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> BaseDomainException.of("user.not-found.by-email", "Không tìm thấy người dùng với email: " + request.getEmail()));

        switch (OtpFor.valueOf(request.getOtpFor())) {
            case VERIFY_EMAIL -> processEmailVerification(user);
            case VERIFY_PHONE -> processPhoneVerification(user);
            case FORGOT_PASSWORD -> processPasswordReset(user);
        }

        // For email/phone verification
        return switch (OtpFor.valueOf(request.getOtpFor())) {
            case VERIFY_EMAIL -> OtpResponse.builder()
                .message("Xác thực email thành công")
                .otpFor(request.getOtpFor())
                .verifiedField("email")
                .build();
            case VERIFY_PHONE -> OtpResponse.builder()
                .message("Xác thực số điện thoại thành công")
                .otpFor(request.getOtpFor())
                .verifiedField("phone")
                .build();
            case FORGOT_PASSWORD -> OtpResponse.builder()
                .message("Xác thực OTP thành công")
                .otpFor(request.getOtpFor())
                .build();
        };
    }


    private void processEmailVerification(User user) {
        if (userRepository.existsByEmailAndStatus(user.getEmail(), UserStatus.EMAIL_VERIFYING)) {
            user.setStatus(UserStatus.PENDING);
            user.setEmailVerified(true);

            if (Boolean.TRUE.equals(user.getPhoneVerified())) {
                user.setStatus(UserStatus.ACTIVE);
                user.setTokenVersion(user.getTokenVersion() + 1);
                try { emailService.notifyUserActivated(user); } catch (Exception e) { log.warn("Failed to send user activation email: {}", e.getMessage()); }
            }
            userRepository.save(user);
            log.info("User email verified and status updated to PENDING: {}", user.getEmail());


            if (Boolean.TRUE.equals(user.getPhoneVerified())) {
                riderProfileRepository.findByUserUserId(user.getUserId())
                    .orElseGet(() -> {
                        RiderProfile rp = RiderProfile.builder()
                            .user(user)
                            .status(RiderProfileStatus.PENDING)
                            .totalRides(0)
                            .totalSpent(java.math.BigDecimal.ZERO)
                            .preferredPaymentMethod(PaymentMethod.WALLET)
                            .createdAt(java.time.LocalDateTime.now())
                            .build();
                        riderProfileRepository.save(rp);
                        log.info("Rider profile created in PENDING for userId={}", user.getUserId());
                        return rp;
                    });
                walletService.createWalletForUser(user.getUserId());
                ensureFallbackContact(user);
            }
        } else {
            log.warn("User email verification attempted but user not in EMAIL_VERIFYING state: {}", user.getEmail());
            try { emailService.notifyUserActivated(user); } catch (Exception e) { log.warn("Failed to send user activation email: {}", e.getMessage()); }
        }
    }

    private void processPhoneVerification(User user) {
        user.setPhoneVerified(true);

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setStatus(UserStatus.ACTIVE);
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        userRepository.save(user);
        log.info("User phone verified: {}", user.getPhone());

        
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            riderProfileRepository.findByUserUserId(user.getUserId())
                .orElseGet(() -> {
                    RiderProfile rp = RiderProfile.builder()
                        .user(user)
                        .status(RiderProfileStatus.PENDING)
                        .totalRides(0)
                        .totalSpent(java.math.BigDecimal.ZERO)
                        .preferredPaymentMethod(PaymentMethod.WALLET)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
                    riderProfileRepository.save(rp);
                    log.info("Rider profile created in PENDING for userId={}", user.getUserId());
                    return rp;
                });
            walletService.createWalletForUser(user.getUserId());
            ensureFallbackContact(user);
        }
    }

    private void ensureFallbackContact(User user) {
        if (user == null || user.getUserId() == null) {
            return;
        }

        if (!emergencyContactService.getContacts(user).isEmpty()) {
            return;
        }

        EmergencyContactRequest request = EmergencyContactRequest.builder()
            .name("Emergency Hotline")
            .phone(sosConfig.getFallbackEmergencyNumber())
            .relationship("Fallback")
            .primary(true)
            .build();

        try {
            emergencyContactService.createContact(user, request);
            log.info("Fallback emergency contact seeded for user {}", user.getUserId());
        } catch (Exception ex) {
            log.warn("Failed to seed fallback emergency contact for user {}: {}", user.getUserId(), ex.getMessage());
        }
    }

    private void processPasswordReset(User user) {
        // Placeholder for any actions needed after password reset OTP verification
        log.info("Password reset OTP verified for user: {}", user.getEmail());
    }
}
