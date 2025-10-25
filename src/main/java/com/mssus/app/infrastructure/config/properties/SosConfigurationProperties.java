package com.mssus.app.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.sos")
@Data
public class SosConfigurationProperties {

    /**
     * Long-press guard on the client before the SOS modal appears.
     * Default 5 seconds per SRS.
     */
    private Duration triggerHoldDuration = Duration.ofSeconds(5);

    /**
     * Time allowed before the system considers the alert unacknowledged.
     * Default 120 seconds per BR-38.
     */
    private Duration acknowledgementTimeout = Duration.ofSeconds(120);

    /**
     * Interval between escalation waves once SLA is breached.
     * Default 30 seconds per BR-38.
     */
    private Duration escalationInterval = Duration.ofSeconds(30);

    /**
     * Fallback emergency phone number when the user has no contacts.
     */
    private String fallbackEmergencyNumber = "113";

    /**
     * Whether the platform should trigger an automated call for the primary contact.
     */
    private boolean autoCallEnabled = true;

    /**
     * Optional list of campus security phone numbers for escalations.
     */
    private List<String> campusSecurityPhones = new ArrayList<>();

    /**
     * Optional list of admin/staff user IDs to broadcast active alerts to.
     */
    private List<Integer> adminUserIds = new ArrayList<>();
}
