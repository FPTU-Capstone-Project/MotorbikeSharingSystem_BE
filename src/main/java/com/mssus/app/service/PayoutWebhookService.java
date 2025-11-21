package com.mssus.app.service;

import com.mssus.app.dto.request.wallet.PayoutWebhookRequest;

public interface PayoutWebhookService {
    /**
     * Handle PayOS payout webhook callback.
     * Verifies signature, finds transaction, and updates status.
     */
    void handlePayoutWebhook(PayoutWebhookRequest webhookRequest, String rawPayload);
}

