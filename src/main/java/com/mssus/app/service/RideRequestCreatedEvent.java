package com.mssus.app.service;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RideRequestCreatedEvent extends ApplicationEvent {
    private final Integer requestId;

    public RideRequestCreatedEvent(Object source, Integer requestId) {
        super(source);
        this.requestId = requestId;
    }
}