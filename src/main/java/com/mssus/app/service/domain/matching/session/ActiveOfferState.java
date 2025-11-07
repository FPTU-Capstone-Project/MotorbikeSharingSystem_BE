package com.mssus.app.service.domain.matching.session;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveOfferState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer driverId;
    private Integer rideId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    public boolean matches(Integer rideId, Integer driverId) {
        return this.rideId != null && this.driverId != null
            && this.rideId.equals(rideId)
            && this.driverId.equals(driverId);
    }
}
