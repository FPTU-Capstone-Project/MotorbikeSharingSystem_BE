package com.mssus.app.service.domain.event;

import com.mssus.app.entity.SosAlert;
import org.springframework.context.ApplicationEvent;

public class SosAlertResolvedEvent extends ApplicationEvent {

    private final transient SosAlert sosAlert;

    public SosAlertResolvedEvent(Object source, SosAlert sosAlert) {
        super(source);
        this.sosAlert = sosAlert;
    }

    public SosAlert getSosAlert() {
        return sosAlert;
    }
}
