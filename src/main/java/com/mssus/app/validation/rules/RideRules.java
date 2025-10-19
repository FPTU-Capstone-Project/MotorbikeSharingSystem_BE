package com.mssus.app.validation.rules;

import com.mssus.app.common.enums.SharedRideRequestStatus;
import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.validation.ValidationContext;
import com.mssus.app.validation.ValidationRule;

import java.util.Map;
import java.util.Objects;

/**
 * A collection of reusable validation rules related to Rides and Ride Requests.
 */
public final class RideRules {

    private RideRules() {}

    public static final ValidationRule<ValidationContext> RIDE_MUST_BE_SCHEDULED = context -> {
        if (context.getRide().getStatus() != SharedRideStatus.SCHEDULED) {
            throw BaseDomainException.of("ride.validation.invalid-state",
                    Map.of("currentState", context.getRide().getStatus()));
        }
    };

    public static final ValidationRule<ValidationContext> DRIVER_MUST_OWN_RIDE = context -> {
        if (!Objects.equals(context.getRide().getDriver().getDriverId(), context.getDriver().getDriverId())) {
            throw BaseDomainException.of("ride.unauthorized.not-owner");
        }
    };

    public static final ValidationRule<ValidationContext> RIDE_MUST_HAVE_SEATS_AVAILABLE = context -> {
        if (context.getRide().getCurrentPassengers() >= context.getRide().getMaxPassengers()) {
            throw BaseDomainException.of("ride.validation.no-seats-available");
        }
    };

    public static final ValidationRule<ValidationContext> RIDE_REQUEST_MUST_BE_PENDING = context -> {
        if (context.getRideRequest().getStatus() != SharedRideRequestStatus.PENDING) {
            throw BaseDomainException.of("ride.validation.request-invalid-state",
                    Map.of("currentState", context.getRideRequest().getStatus()));
        }
    };
}