package com.mssus.app.messaging.listener;

import com.mssus.app.messaging.dto.DriverLocationUpdateMessage;
import com.mssus.app.entity.DriverProfile;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.repository.DriverProfileRepository;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.common.enums.SharedRideStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.messaging.ride", name = "enabled", havingValue = "true")
public class DriverLocationUpdateListener {

    private final DriverProfileRepository driverRepository;
    private final SharedRideRepository rideRepository;
    
    // Future: inject RoutingService for ETA recalculation
    // Future: inject NotificationService for rider updates

    @RabbitListener(queues = "${app.messaging.ride.driver-location-queue}", autoStartup = "true")
    public void onDriverLocationUpdate(@Payload DriverLocationUpdateMessage message) {
        if (message == null || message.getDriverId() == null) {
            log.warn("Received invalid location update message");
            return;
        }

        if (message.getLatitude() == null || message.getLongitude() == null) {
            log.warn("Location update for driver {} missing coordinates", message.getDriverId());
            return;
        }

        // Validate timestamp - ignore stale updates
        if (message.getTimestamp() != null) {
            long ageSeconds = Instant.now().getEpochSecond() - message.getTimestamp().getEpochSecond();
            if (ageSeconds > 60) {
                log.debug("Ignoring stale location update for driver {} (age: {}s)", 
                    message.getDriverId(), ageSeconds);
                return;
            }
        }

        try {
            updateDriverLocation(message);
            
            if (message.getRideId() != null) {
                updateRideProgress(message);
            }
        } catch (Exception e) {
            log.error("Error processing location update for driver {}", 
                message.getDriverId(), e);
        }
    }

    private void updateDriverLocation(DriverLocationUpdateMessage message) {
        DriverProfile driver = driverRepository.findById(message.getDriverId()).orElse(null);
        if (driver == null) {
            log.warn("Driver {} not found for location update", message.getDriverId());
            return;
        }

        // TODO: Future implementation - requires adding location fields to DriverProfile entity
        // Fields needed: lastLatitude, lastLongitude, lastLocationUpdate
        // For now, just log the update
        log.debug("Location update received for driver {} at ({}, {}) - persistence pending schema update",
            message.getDriverId(), message.getLatitude(), message.getLongitude());
        
        // Future implementation:
        // driver.setLastLatitude(message.getLatitude());
        // driver.setLastLongitude(message.getLongitude());
        // driver.setLastLocationUpdate(message.getTimestamp() != null ? 
        //     message.getTimestamp() : Instant.now());
        // driverRepository.save(driver);
    }

    private void updateRideProgress(DriverLocationUpdateMessage message) {
        SharedRide ride = rideRepository.findById(message.getRideId()).orElse(null);
        if (ride == null) {
            log.debug("Ride {} not found for location update", message.getRideId());
            return;
        }

        if (ride.getStatus() != SharedRideStatus.ONGOING) {
            log.debug("Ignoring location update for non-ongoing ride {} (status: {})",
                message.getRideId(), ride.getStatus());
            return;
        }

        // Future enhancements:
        // 1. Calculate new ETA based on current location and route
        // 2. Detect if driver is off-route
        // 3. Update estimated pickup/dropoff times for riders
        // 4. Send notifications if ETA changes significantly
        // 5. Trigger proximity alerts when driver approaches pickup/dropoff

        log.trace("Processed location update for ongoing ride {}", message.getRideId());
    }
}

