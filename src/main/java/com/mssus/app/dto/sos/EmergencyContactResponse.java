package com.mssus.app.dto.sos;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class EmergencyContactResponse {
    Integer contactId;
    String name;
    String phone;
    String relationship;
    Boolean primary;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
