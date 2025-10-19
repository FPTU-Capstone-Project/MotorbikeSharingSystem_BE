package com.mssus.app.dto.request.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideCompleteSettlementRequest {
    private Integer riderId;
    private Integer rideRequestId;
    private Integer driverId;
    private String note;
}