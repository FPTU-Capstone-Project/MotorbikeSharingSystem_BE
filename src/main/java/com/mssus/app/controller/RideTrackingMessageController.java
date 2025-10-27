package com.mssus.app.controller;

import com.mssus.app.dto.domain.ride.LocationPoint;
import com.mssus.app.service.RideTrackingService;
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

@Controller
@RequiredArgsConstructor
@Slf4j
public class RideTrackingMessageController {

    private final RideTrackingService rideTrackingService;

    @MessageMapping("/ride.track.{rideId}")
    public void receiveGpsPoints(@DestinationVariable Integer rideId, List<LocationPoint> points, Principal principal) {
        log.debug("Received {} GPS points for ride {} from driver {}", points.size(), rideId, principal.getName());
        rideTrackingService.appendGpsPoints(rideId, points, principal.getName());
    }
}