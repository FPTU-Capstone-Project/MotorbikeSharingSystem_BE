package com.mssus.app.controller;

import com.mssus.app.dto.domain.ride.LocationPoint;
import com.mssus.app.service.RideTrackingService;
import com.mssus.app.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RideTrackingMessageController {

    private final RideTrackingService rideTrackingService;
    private final AuthService authService;

    @MessageMapping("/ride.track.{rideId}")
    public void receiveGpsPoints(@DestinationVariable Integer rideId, List<LocationPoint> points, Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        Map<String, Object> ctx = null;
        if (userId != null) {
            try {
                ctx = authService.getUserContext(Integer.parseInt(userId));
            } catch (NumberFormatException ignored) {
                log.warn("WS principal name is not numeric userId: {}", userId);
            }
        }
        String activeProfile = ctx != null ? (String) ctx.get("active_profile") : null;

        log.debug("Received {} GPS points for ride {} from user {} (active_profile={})", points.size(), rideId, userId, activeProfile);
        rideTrackingService.appendGpsPoints(rideId, points, userId, activeProfile);
    }
}
