package com.mssus.app.controller;

import com.mssus.app.common.enums.RouteType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.route.CreateRouteTemplateRequest;
import com.mssus.app.dto.response.PageResponse;
import com.mssus.app.dto.response.route.PricingContextResponse;
import com.mssus.app.dto.response.route.RouteDetailResponse;
import com.mssus.app.service.RouteTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/admin/routes")
@RequiredArgsConstructor
@Tag(name = "Admin Route Management", description = "Manage predefined campus routes")
@SecurityRequirement(name = "bearerAuth")
public class AdminRouteController {

    private final RouteTemplateService routeTemplateService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Create template route",
        description = "Builds a predefined route using OSRM + pricing service. Default price is auto-calculated.",
        responses = @ApiResponse(
            responseCode = "201",
            description = "Route created",
            content = @Content(schema = @Schema(implementation = RouteDetailResponse.class))
        )
    )
    public ResponseEntity<RouteDetailResponse> createRoute(@Valid @RequestBody CreateRouteTemplateRequest request) {
        RouteDetailResponse response = routeTemplateService.createTemplateRoute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "List predefined routes",
        description = "Returns paginated routes optionally filtered by route type."
    )
    public ResponseEntity<PageResponse<RouteDetailResponse>> listRoutes(
        @RequestParam(name = "routeType", required = false) String routeType,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        RouteType filter = parseRouteType(routeType);
        Page<RouteDetailResponse> page = routeTemplateService.listRoutes(filter, pageable);

        PageResponse<RouteDetailResponse> response = PageResponse.<RouteDetailResponse>builder()
            .data(page.getContent())
            .pagination(PageResponse.PaginationInfo.builder()
                .page(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalRecords(page.getTotalElements())
                .build())
            .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{routeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Route detail",
        description = "Returns a single predefined route with pricing preview"
    )
    public ResponseEntity<RouteDetailResponse> getRoute(@PathVariable Integer routeId) {
        return ResponseEntity.ok(routeTemplateService.getRouteDetail(routeId));
    }

    @GetMapping("/pricing-context")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Active pricing config",
        description = "Exposes the active pricing configuration to assist admins when creating predefined routes"
    )
    public ResponseEntity<PricingContextResponse> getPricingContext() {
        return ResponseEntity.ok(routeTemplateService.getActivePricingContext());
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Preview route pricing",
        description = "Calculates distance, polyline, and fare without persisting the route"
    )
    public ResponseEntity<RouteDetailResponse> previewRoute(@Valid @RequestBody CreateRouteTemplateRequest request) {
        return ResponseEntity.ok(routeTemplateService.previewTemplateRoute(request));
    }

    private RouteType parseRouteType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return RouteType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BaseDomainException.validation("Unsupported routeType value: " + value);
        }
    }
}
