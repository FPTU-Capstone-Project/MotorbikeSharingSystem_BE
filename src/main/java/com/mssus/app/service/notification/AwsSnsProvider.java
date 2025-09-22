package com.mssus.app.service.notification;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.mssus.app.dto.request.SmsRequest;
import com.mssus.app.dto.response.notification.SmsProvider;
import com.mssus.app.dto.response.notification.SmsResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name = "app.sms.aws-sns.enabled", havingValue = "true")
@Slf4j
public class AwsSnsProvider implements SmsProvider {

    private final AmazonSNS snsClient;

    public AwsSnsProvider(AmazonSNS snsClient) {
        this.snsClient = snsClient;
    }

    @Override
    public SmsResult sendSms(SmsRequest request) {
        try {
            PublishRequest publishRequest = new PublishRequest()
                .withPhoneNumber(request.to())
                .withMessage(request.message());

            PublishResult result = snsClient.publish(publishRequest);

            return SmsResult.success(result.getMessageId(), this, BigDecimal.ZERO);

        } catch (Exception e) {
            log.error("AWS SNS SMS send failed", e);
            return SmsResult.failure("AWS SNS error: " + e.getMessage(), this);
        }
    }

    @Override
    public String getName() {
        return "AWS-SNS";
    }

    @Override
    public int getPriority() {
        return 1;
    }

}