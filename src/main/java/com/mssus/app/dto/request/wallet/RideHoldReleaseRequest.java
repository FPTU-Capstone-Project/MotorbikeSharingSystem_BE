package com.mssus.app.dto.request.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideHoldReleaseRequest {
    private Integer riderId;
    private Integer rideRequestId;
    private String note;
}
