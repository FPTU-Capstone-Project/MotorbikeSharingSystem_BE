package com.mssus.app.service.domain.event;

import com.mssus.app.entity.EmergencyContact;
import com.mssus.app.entity.SosAlert;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class SosAlertTriggeredEvent extends ApplicationEvent {

    private final transient SosAlert sosAlert;
    private final transient List<EmergencyContact> contacts;

    public SosAlertTriggeredEvent(Object source, SosAlert sosAlert, List<EmergencyContact> contacts) {
        super(source);
        this.sosAlert = sosAlert;
        this.contacts = contacts;
    }

    public SosAlert getSosAlert() {
        return sosAlert;
    }

    public List<EmergencyContact> getContacts() {
        return contacts;
    }
}
