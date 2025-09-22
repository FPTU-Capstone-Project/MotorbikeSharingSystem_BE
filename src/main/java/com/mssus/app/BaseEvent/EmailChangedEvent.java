package com.mssus.app.BaseEvent;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailChangedEvent extends DomainEvent{
    private final Integer userId;
    private final String oldEmail;
    private final String newEmail;


    public EmailChangedEvent(Integer userId, String oldEmail, String newEmail) {
        super();
        this.userId = userId;
        this.oldEmail = oldEmail;
        this.newEmail = newEmail;
    }
}
