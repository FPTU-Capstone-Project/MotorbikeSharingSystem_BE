package com.mssus.app.messaging;

import com.mssus.app.messaging.dto.DriverLocationUpdateMessage;

public interface RideEventPublisher {

    void publishRideRequestCreated(Integer requestId);

    void publishDriverLocationUpdate(DriverLocationUpdateMessage message);
}
