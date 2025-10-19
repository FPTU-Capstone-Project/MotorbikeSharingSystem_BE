package com.mssus.app.worker;

import com.mssus.app.common.enums.SharedRideStatus;
import com.mssus.app.entity.SharedRide;
import com.mssus.app.repository.SharedRideRepository;
import com.mssus.app.service.RideTrackingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideAutoStarter {
    private final SharedRideRepository rideRepository;
    private final RideTrackingService trackingService;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoStartScheduledRides() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now;
        log.info("Checking for rides scheduled before: {}", cutoff);

        List<SharedRide> readyRides = rideRepository.findScheduledAndOverdue(now);
        log.info("Found {} rides to auto-start", readyRides.size());

        for (SharedRide ride : readyRides) {
            log.info("Processing ride {} with scheduled time {}",
                ride.getSharedRideId(), ride.getScheduledTime());
            if (ride.getStatus() == SharedRideStatus.SCHEDULED) {
                // Soft proximity check (optional: Log warn if far, but don't block)
                // double distFromStart = calculateDistance(ride.getCurrentLat() ?? ride.getStartLat(), ...);  // If GPS already
                // if (distFromStart > 0.5) log.warn("Auto-starting ride {} far from start: {} km", ride.getSharedRideId(), distFromStart);
                ride.setStatus(SharedRideStatus.ONGOING);
                ride.setStartedAt(now);
                rideRepository.save(ride);

                trackingService.startTracking(ride.getSharedRideId());  // Notify app: "Begin /track calls"
                log.info("Auto-started ride {} (solo) at {}", ride.getSharedRideId(), now);
            }
        }
    }
}
