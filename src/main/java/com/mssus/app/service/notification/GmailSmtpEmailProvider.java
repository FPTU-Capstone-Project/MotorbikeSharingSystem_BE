package com.mssus.app.service.notification;

import com.mssus.app.config.properties.EmailConfigurationProperties;
import com.mssus.app.dto.response.notification.EmailProvider;
import com.mssus.app.dto.response.notification.EmailRequest;
import com.mssus.app.dto.response.notification.EmailResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.gmail-smtp.enabled", havingValue = "true")
@Slf4j
public class GmailSmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final EmailTemplateService templateService;
    private final EmailConfigurationProperties emailConfig;

    public GmailSmtpEmailProvider(
            JavaMailSender mailSender,
            EmailTemplateService templateService,
            EmailConfigurationProperties emailConfig) {
        this.mailSender = mailSender;
        this.templateService = templateService;
        this.emailConfig = emailConfig;
    }

    @Override
    public EmailResult sendEmail(EmailRequest request) {
        try {
            // Render HTML template
            String htmlContent = templateService.renderTemplate(
                request.templateName(), request.templateVariables());

            // Create MIME message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email properties
            helper.setFrom(emailConfig.getFromAddress(), emailConfig.getFromName());
            helper.setTo(request.to());
            helper.setSubject(request.subject());
            helper.setText(htmlContent, true); // true = HTML content

            // Add text version for better compatibility
            String textContent = templateService.renderTextTemplate(
                request.templateName(), request.templateVariables());
            helper.setText(textContent, htmlContent);

            // Send email
            mailSender.send(message);

            // Generate message ID (Gmail doesn't return one via SMTP)
            String messageId = generateMessageId();

            log.info("Gmail SMTP email sent successfully: {}", messageId);
            return EmailResult.success(messageId, this);

        } catch (MessagingException e) {
            log.error("Gmail SMTP messaging error", e);
            return EmailResult.failure("Gmail SMTP messaging error: " + e.getMessage(), this);
        } catch (Exception e) {
            log.error("Gmail SMTP email send failed", e);
            return EmailResult.failure("Gmail SMTP error: " + e.getMessage(), this);
        }
    }

    @Override
    public String getName() {
        return "Gmail-SMTP";
    }

    @Override
    public int getPriority() {
        return 1;
    }


    private String generateMessageId() {
        // Generate a unique message ID since Gmail SMTP doesn't return one
        return "gmail-" + System.currentTimeMillis() + "-" +
               Integer.toHexString((int)(Math.random() * 1000000));
    }
}