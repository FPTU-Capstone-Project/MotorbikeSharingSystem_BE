package com.mssus.app.controller;

import com.mssus.app.dto.response.ride.RouteSummaryResponse;
import com.mssus.app.entity.Route;
import com.mssus.app.service.RouteAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Predefined campus routes catalogue")
public class RouteController {

    private final RouteAssignmentService routeAssignmentService;

    @GetMapping("/templates")
    @Operation(summary = "List template routes", description = "Returns all predefined routes available for ride creation")
    public ResponseEntity<List<RouteSummaryResponse>> listTemplateRoutes() {
        List<RouteSummaryResponse> routes = routeAssignmentService.getTemplateRoutes()
            .stream()
            .map(this::toRouteSummary)
            .toList();
        return ResponseEntity.ok(routes);
    }

    private RouteSummaryResponse toRouteSummary(Route route) {
        if (route == null) {
            return null;
        }
        return RouteSummaryResponse.builder()
            .routeId(route.getRouteId())
            .name(route.getName())
            .routeType(route.getRouteType() != null ? route.getRouteType().name() : null)
            .defaultPrice(route.getDefaultPrice())
            .polyline(route.getPolyline())
            .validFrom(route.getValidFrom())
            .validUntil(route.getValidUntil())
            .build();
    }
}
