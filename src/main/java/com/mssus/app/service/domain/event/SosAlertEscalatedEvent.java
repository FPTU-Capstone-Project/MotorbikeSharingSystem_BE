package com.mssus.app.service.domain.event;

import com.mssus.app.entity.SosAlert;
import org.springframework.context.ApplicationEvent;

public class SosAlertEscalatedEvent extends ApplicationEvent {

    private final transient SosAlert sosAlert;
    private final boolean campusSecurityNotice;

    public SosAlertEscalatedEvent(Object source, SosAlert sosAlert, boolean campusSecurityNotice) {
        super(source);
        this.sosAlert = sosAlert;
        this.campusSecurityNotice = campusSecurityNotice;
    }

    public SosAlert getSosAlert() {
        return sosAlert;
    }

    public boolean isCampusSecurityNotice() {
        return campusSecurityNotice;
    }
}
