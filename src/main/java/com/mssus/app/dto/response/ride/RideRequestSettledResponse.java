package com.mssus.app.dto.response.ride;

import java.math.BigDecimal;

public record RideRequestSettledResponse(
    BigDecimal driverEarnings,
    BigDecimal systemCommission
) {}
