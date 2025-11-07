package com.mssus.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class RideRequestCreatedMessage {

    Integer requestId;

    /**
     * ISO-8601 timestamp representing when the event was emitted.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant occurredAt;

    String eventType;

    String version;

    public static RideRequestCreatedMessage from(Integer requestId) {
        return RideRequestCreatedMessage.builder()
            .requestId(requestId)
            .occurredAt(Instant.now())
            .eventType("ride.request.created")
            .version("1")
            .build();
    }
}
