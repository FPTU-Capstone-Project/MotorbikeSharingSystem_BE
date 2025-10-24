package com.mssus.app.service;

import com.mssus.app.common.enums.SosAlertEventType;
import com.mssus.app.entity.SosAlert;
import com.mssus.app.entity.SosAlertEvent;
import com.mssus.app.repository.SosAlertEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SosAlertEventService {

    private final SosAlertEventRepository eventRepository;

    public SosAlertEvent record(SosAlert alert, SosAlertEventType type, String description, String metadata) {
        SosAlertEvent event = SosAlertEvent.builder()
            .sosAlert(alert)
            .eventType(type)
            .description(description)
            .metadata(metadata)
            .build();
        return eventRepository.save(event);
    }
}
