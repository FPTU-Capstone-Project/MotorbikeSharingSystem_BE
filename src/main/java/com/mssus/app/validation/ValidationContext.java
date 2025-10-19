package com.mssus.app.validation;

import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.entity.SharedRideRequest;
import com.mssus.app.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * A container for all data required for a validation operation.
 * This object is passed to each {@link ValidationRule}.
 * Use the builder to construct the context with relevant entities.
 */
@Getter
@Builder
public class ValidationContext {
    // User/Profile context
    private final User user;
    private final DriverProfile driver;

    // Ride context
    private final SharedRide ride;
    private final SharedRideRequest rideRequest;
    private final BigDecimal amount;
}