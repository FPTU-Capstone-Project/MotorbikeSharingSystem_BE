package com.mssus.app.service;

import com.mssus.app.common.enums.RouteType;
import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.route.CreateRouteTemplateRequest;
import com.mssus.app.dto.request.route.RouteEndpointRequest;
import com.mssus.app.dto.response.route.FareTierResponse;
import com.mssus.app.dto.response.route.PricingContextResponse;
import com.mssus.app.dto.response.route.RouteDetailResponse;
import com.mssus.app.dto.response.route.RouteEndpointResponse;
import com.mssus.app.dto.response.route.RoutePricingPreviewResponse;
import com.mssus.app.dto.response.RouteResponse;
import com.mssus.app.entity.FareTier;
import com.mssus.app.entity.Location;
import com.mssus.app.entity.PricingConfig;
import com.mssus.app.entity.Route;
import com.mssus.app.repository.FareTierRepository;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.repository.RouteRepository;
import com.mssus.app.service.RoutingService;
import com.mssus.app.service.domain.pricing.PricingService;
import com.mssus.app.service.domain.pricing.model.FareBreakdown;
import com.mssus.app.service.domain.pricing.model.PriceInput;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class RouteTemplateService {

    private final RouteRepository routeRepository;
    private final LocationRepository locationRepository;
    private final RoutingService routingService;
    private final PricingService pricingService;
    private final PricingConfigRepository pricingConfigRepository;
    private final FareTierRepository fareTierRepository;

    public RouteDetailResponse createTemplateRoute(CreateRouteTemplateRequest request) {
        EndpointPair endpoints = resolveEndpoints(request, true);
        Location from = endpoints.from();
        Location to = endpoints.to();

        if (from.getLocationId().equals(to.getLocationId())) {
            throw BaseDomainException.validation("Start and end locations cannot be the same");
        }

        LocalDateTime validFrom = request.validFrom() != null ? request.validFrom() : LocalDateTime.now();
        LocalDateTime validUntil = request.validUntil();
        if (validUntil != null && !validUntil.isAfter(validFrom)) {
            throw BaseDomainException.validation("validUntil must be greater than validFrom");
        }

        ComputedRoute computed = computeRouteAndPrice(from, to);

        Route template = Route.builder()
            .name(request.name())
            .routeType(RouteType.TEMPLATE)
            .fromLocation(from)
            .toLocation(to)
            .defaultPrice(computed.pricing().total() != null ? computed.pricing().total().amount() : null)
            .polyline(computed.route().polyline())
            .validFrom(validFrom)
            .validUntil(validUntil)
            .distanceMeters(computed.route().distance())
            .durationSeconds(computed.route().time())
            .build();

        Route saved = routeRepository.save(template);
        return mapToDetail(saved, computed.pricing());
    }

    public RouteDetailResponse previewTemplateRoute(CreateRouteTemplateRequest request) {
        EndpointPair endpoints = resolveEndpoints(request, false);
        Location from = endpoints.from();
        Location to = endpoints.to();

        if (from == null || to == null) {
            throw BaseDomainException.validation("Both start and end locations are required");
        }

        if (hasSameCoordinates(from, to)) {
            throw BaseDomainException.validation("Start and end locations cannot be the same");
        }

        ComputedRoute computed = computeRouteAndPrice(from, to);
        Route transientRoute = Route.builder()
            .name(request.name())
            .routeType(RouteType.TEMPLATE)
            .fromLocation(from)
            .toLocation(to)
            .defaultPrice(computed.pricing().total() != null ? computed.pricing().total().amount() : null)
            .polyline(computed.route().polyline())
            .validFrom(request.validFrom())
            .validUntil(request.validUntil())
            .distanceMeters(computed.route().distance())
            .durationSeconds(computed.route().time())
            .build();

        return mapToDetail(transientRoute, computed.pricing());
    }

    @Transactional(readOnly = true)
    public Page<RouteDetailResponse> listRoutes(RouteType routeType, Pageable pageable) {
        Page<Route> routes = routeType != null
            ? routeRepository.findByRouteType(routeType, pageable)
            : routeRepository.findAll(pageable);
        return routes.map(route -> mapToDetail(route, null));
    }

    @Transactional(readOnly = true)
    public RouteDetailResponse getRouteDetail(Integer routeId) {
        Route route = routeRepository.findById(routeId)
            .orElseThrow(() -> BaseDomainException.validation("Route not found for id " + routeId));

        FareBreakdown preview = route.getDistanceMeters() != null
            ? pricingService.quote(new PriceInput(route.getDistanceMeters(), Optional.empty(), null))
            : null;

        return mapToDetail(route, preview);
    }

    @Transactional(readOnly = true)
    public PricingContextResponse getActivePricingContext() {
        PricingConfig config = pricingConfigRepository.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"));

        List<FareTier> tiers = fareTierRepository.findByPricingConfig_PricingConfigId(config.getPricingConfigId());

        return PricingContextResponse.builder()
            .pricingConfigId(config.getPricingConfigId())
            .version(config.getVersion())
            .validFrom(config.getValidFrom())
            .validUntil(config.getValidUntil())
            .systemCommissionRate(config.getSystemCommissionRate())
            .fareTiers(tiers.stream()
                .filter(t -> t.getIsActive() == null || Boolean.TRUE.equals(t.getIsActive()))
                .map(this::toTierResponse)
                .toList())
            .build();
    }

    private EndpointPair resolveEndpoints(CreateRouteTemplateRequest request, boolean persistIfMissing) {
        Location from = resolveLocation(request.from(), "from", persistIfMissing);
        Location to = resolveLocation(request.to(), "to", persistIfMissing);
        return new EndpointPair(from, to);
    }

    private Location resolveLocation(RouteEndpointRequest endpoint, String pointLabel, boolean persistIfMissing) {
        if (endpoint == null) {
            throw BaseDomainException.validation(pointLabel + " endpoint is required");
        }
        if (endpoint.hasLocationId()) {
            return locationRepository.findById(endpoint.locationId())
                .orElseThrow(() -> BaseDomainException.validation(
                    pointLabel + " location not found for id " + endpoint.locationId()));
        }
        if (!endpoint.hasCoordinates()) {
            throw BaseDomainException.validation(
                "Either locationId or coordinates must be provided for " + pointLabel + " point");
        }

        Double lat = endpoint.coordinates().latitude();
        Double lng = endpoint.coordinates().longitude();

        Optional<Location> existing = locationRepository.findByLatAndLng(lat, lng);
        if (existing.isPresent()) {
            return existing.get();
        }

        Location newLocation = new Location();
        newLocation.setLat(lat);
        newLocation.setLng(lng);
        if (endpoint.label() != null && !endpoint.label().isBlank()) {
            newLocation.setName(endpoint.label().trim());
        } else {
            newLocation.setName(pointLabel);
        }
        String address = (endpoint.address() != null && !endpoint.address().isBlank())
            ? endpoint.address().trim()
            : routingService.getAddressFromCoordinates(lat, lng);
        newLocation.setAddress(address);
        newLocation.setIsPoi(false);
        newLocation.setCreatedAt(LocalDateTime.now());

        return persistIfMissing ? locationRepository.save(newLocation) : newLocation;
    }

    private boolean hasSameCoordinates(Location from, Location to) {
        if (from == null || to == null || from.getLat() == null || to.getLat() == null) {
            return false;
        }
        return Double.compare(from.getLat(), to.getLat()) == 0
            && Double.compare(from.getLng(), to.getLng()) == 0;
    }

    private ComputedRoute computeRouteAndPrice(Location from, Location to) {
        RouteResponse osrmRoute = routingService.getRoute(
            from.getLat(), from.getLng(),
            to.getLat(), to.getLng()
        );

        FareBreakdown pricing = pricingService.quote(new PriceInput(
            osrmRoute.distance(),
            Optional.empty(),
            null
        ));

        return new ComputedRoute(osrmRoute, pricing);
    }

    private RouteDetailResponse mapToDetail(Route route, FareBreakdown pricing) {
        if (route == null) {
            return null;
        }
        return RouteDetailResponse.builder()
            .routeId(route.getRouteId())
            .name(route.getName())
            .routeType(route.getRouteType() != null ? route.getRouteType().name() : null)
            .from(toEndpoint(route.getFromLocation()))
            .to(toEndpoint(route.getToLocation()))
            .defaultPrice(route.getDefaultPrice())
            .distanceMeters(route.getDistanceMeters())
            .durationSeconds(route.getDurationSeconds())
            .polyline(route.getPolyline())
            .validFrom(route.getValidFrom())
            .validUntil(route.getValidUntil())
            .createdAt(route.getCreatedAt())
            .updatedAt(route.getUpdatedAt())
            .pricingPreview(pricing != null ? toPricingPreview(pricing) : null)
            .build();
    }

    private RouteEndpointResponse toEndpoint(Location location) {
        if (location == null) {
            return null;
        }
        return RouteEndpointResponse.builder()
            .locationId(location.getLocationId())
            .name(location.getName())
            .address(location.getAddress())
            .latitude(location.getLat())
            .longitude(location.getLng())
            .isPoi(location.getIsPoi())
            .build();
    }

    private RoutePricingPreviewResponse toPricingPreview(FareBreakdown breakdown) {
        return RoutePricingPreviewResponse.builder()
            .pricingVersion(breakdown.pricingVersion())
            .subtotal(breakdown.subtotal() != null ? breakdown.subtotal().amount() : null)
            .discount(breakdown.discount() != null ? breakdown.discount().amount() : null)
            .total(breakdown.total() != null ? breakdown.total().amount() : null)
            .commissionRate(breakdown.commissionRate())
            .build();
    }

    private FareTierResponse toTierResponse(FareTier tier) {
        return FareTierResponse.builder()
            .tierLevel(tier.getTierLevel())
            .minKm(tier.getMinKm())
            .maxKm(tier.getMaxKm())
            .amount(tier.getAmount())
            .description(tier.getDescription())
            .build();
    }

    private record EndpointPair(Location from, Location to) {}

    private record ComputedRoute(RouteResponse route, FareBreakdown pricing) {}
}
