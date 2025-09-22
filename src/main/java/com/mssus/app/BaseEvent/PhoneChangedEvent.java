package com.mssus.app.BaseEvent;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PhoneChangedEvent extends DomainEvent{
    private final Integer userId;
    private final String oldPhone;
    private final String newPhone;

    public PhoneChangedEvent(Integer userId, String oldPhone, String newPhone) {
        super();
        this.userId = userId;
        this.oldPhone = oldPhone;
        this.newPhone = newPhone;
    }
}
