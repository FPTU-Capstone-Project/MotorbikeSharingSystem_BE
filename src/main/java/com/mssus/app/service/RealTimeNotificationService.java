package com.mssus.app.service;

import com.mssus.app.dto.domain.notification.DriverRideOfferNotification;
import com.mssus.app.dto.domain.notification.RiderMatchStatusNotification;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.User;

public interface RealTimeNotificationService {
    void notifyDriverOffer(DriverProfile driver, DriverRideOfferNotification payload);

    void notifyRiderStatus(User riderUser, RiderMatchStatusNotification payload);

    void notifyDriverJoinRequest(DriverProfile driver, DriverRideOfferNotification payload);

    void notifyDriverTrackingStart(DriverProfile driver, Integer rideId);
}
