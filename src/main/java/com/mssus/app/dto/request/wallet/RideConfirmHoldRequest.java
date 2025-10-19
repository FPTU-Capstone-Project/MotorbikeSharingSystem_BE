package com.mssus.app.dto.request.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideConfirmHoldRequest {
    private Integer riderId;
    private Integer rideRequestId;
    private BigDecimal amount;
    private String note;
}