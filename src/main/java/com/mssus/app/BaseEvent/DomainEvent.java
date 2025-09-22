package com.mssus.app.BaseEvent;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public abstract class DomainEvent {
    private final Instant occurredOn;
    private final UUID eventId;

    protected DomainEvent() {
        this.occurredOn = Instant.now();
        this.eventId = UUID.randomUUID();
    }
}
