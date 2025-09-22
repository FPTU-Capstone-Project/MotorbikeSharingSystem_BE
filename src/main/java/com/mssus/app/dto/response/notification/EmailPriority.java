package com.mssus.app.dto.response.notification;

public enum EmailPriority {
    LOW(3), NORMAL(2), HIGH(1), URGENT(0);

    private final int value;

    EmailPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
