package com.mssus.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class MatchingCommandMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    MatchingCommandType commandType;
    Integer requestId;
    Integer rideId;
    Integer driverId;
    Boolean broadcast;
    Integer candidateIndex;
    Map<String, Object> attributes;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant occurredAt;
    String correlationId;

    public static MatchingCommandMessage sendNext(Integer requestId, int candidateIndex) {
        return builder()
            .commandType(MatchingCommandType.SEND_NEXT_OFFER)
            .requestId(requestId)
            .candidateIndex(candidateIndex)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }

    public static MatchingCommandMessage driverTimeout(Integer requestId, Integer driverId, Integer rideId) {
        return builder()
            .commandType(MatchingCommandType.DRIVER_TIMEOUT)
            .requestId(requestId)
            .driverId(driverId)
            .rideId(rideId)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }

    public static MatchingCommandMessage broadcastTimeout(Integer requestId) {
        return builder()
            .commandType(MatchingCommandType.BROADCAST_TIMEOUT)
            .requestId(requestId)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }

    public static MatchingCommandMessage driverResponse(Integer requestId,
                                                         Integer driverId,
                                                         Integer rideId,
                                                         boolean broadcast,
                                                         Map<String, Object> attributes) {
        return builder()
            .commandType(MatchingCommandType.DRIVER_RESPONSE)
            .requestId(requestId)
            .driverId(driverId)
            .rideId(rideId)
            .broadcast(broadcast)
            .attributes(attributes)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }

    public static MatchingCommandMessage cancel(Integer requestId) {
        return builder()
            .commandType(MatchingCommandType.CANCEL_MATCHING)
            .requestId(requestId)
            .occurredAt(Instant.now())
            .correlationId(UUID.randomUUID().toString())
            .build();
    }
}
