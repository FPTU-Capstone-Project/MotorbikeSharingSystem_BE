package com.mssus.app.service;

import com.mssus.app.common.enums.RouteType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.Route;
import com.mssus.app.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteAssignmentService {

    private final RouteRepository routeRepository;

    /**
     * Resolves a route for a ride. When a template route is provided we reuse it,
     * otherwise a ride-specific snapshot is created to preserve history.
     */
    @Transactional
    public Route resolveRoute(Integer routeId, Location start, Location end, String polyline) {
        if (routeId != null) {
            Route template = routeRepository.findById(routeId)
                .orElseThrow(() -> BaseDomainException.validation("Route not found for id " + routeId));

            validateTemplateAlignment(template, start, end);
            return template;
        }

        Route snapshot = Route.builder()
            .name(buildRouteName(start, end))
            .routeType(RouteType.CUSTOM)
            .fromLocation(start)
            .toLocation(end)
            .polyline(polyline)
            .validFrom(LocalDateTime.now())
            .build();

        return routeRepository.save(snapshot);
    }

    @Transactional(readOnly = true)
    public List<Route> getTemplateRoutes() {
        return routeRepository.findByRouteType(RouteType.TEMPLATE);
    }

    @Transactional(readOnly = true)
    public Route getRoute(Integer routeId) {
        if (routeId == null) {
            return null;
        }
        return routeRepository.findById(routeId)
            .orElseThrow(() -> BaseDomainException.validation("Route not found for id " + routeId));
    }

    private void validateTemplateAlignment(Route template, Location start, Location end) {
        boolean mismatchedStart = template.getFromLocation() != null
            && start != null
            && !template.getFromLocation().getLocationId().equals(start.getLocationId());
        boolean mismatchedEnd = template.getToLocation() != null
            && end != null
            && !template.getToLocation().getLocationId().equals(end.getLocationId());

        if (mismatchedStart || mismatchedEnd) {
            log.warn("Template route {} does not align with ride start/end locations (start mismatch: {}, end mismatch: {})",
                template.getRouteId(), mismatchedStart, mismatchedEnd);
        }
    }

    private String generateSnapshotCode() {
        return "ROUTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String buildRouteName(Location start, Location end) {
        String startName = Optional.ofNullable(start)
            .map(Location::getName)
            .orElse("Start");
        String endName = Optional.ofNullable(end)
            .map(Location::getName)
            .orElse("End");
        return String.format("%s to %s", startName, endName);
    }
}
