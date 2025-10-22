package com.mssus.app.service.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.QuoteRequest;
import com.mssus.app.entity.Location;
import com.mssus.app.service.pricing.PricingService;
import com.mssus.app.service.pricing.QuoteCache;
import com.mssus.app.service.pricing.model.PriceInput;
import com.mssus.app.service.pricing.model.Quote;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.service.QuoteService;
import com.mssus.app.service.RoutingService;
import com.mssus.app.util.PolylineDistance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class QuoteServiceImpl implements QuoteService {
    private final QuoteCache quoteCache;
    private final RoutingService routingService;
    private final PricingService pricingService;
    private final LocationRepository locationRepository;

    private static final double LOCATION_TOLERANCE = 0.001;

    @Override
    @Transactional
    public Quote generateQuote(QuoteRequest request, int userId) {
        Location pickupLoc;
        Location dropoffLoc;

        Location fptuLoc = locationRepository.findByLatAndLng(10.841480, 106.809844)
            .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                "FPT University location not found"));

        Location schLoc = locationRepository.findByLatAndLng(10.8753395, 106.8000331)
            .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                "Student Culture House location not found"));

        pickupLoc = findOrCreateLocation(request.pickupLocationId(), request.pickup());
        dropoffLoc = findOrCreateLocation(request.dropoffLocationId(), request.dropoff());

        if (Objects.equals(pickupLoc.getLat(), dropoffLoc.getLat()) &&
            Objects.equals(pickupLoc.getLng(), dropoffLoc.getLng())) {
            throw BaseDomainException.formatted("ride.validation.invalid-location",
                "Pickup and dropoff locations cannot be the same");
        }

        validateRequiredLocations(pickupLoc, dropoffLoc, fptuLoc, schLoc);

        double centerLat = fptuLoc.getLat();
        double centerLng = fptuLoc.getLng();
        double maxRadiusKm = 25.0; //TODO: Configurable via rideConfig.getServiceArea().getRadiusKm()

        double pickupDistKm = PolylineDistance.haversineMeters(centerLat, centerLng, pickupLoc.getLat(), pickupLoc.getLng()) / 1000.0;
        double dropoffDistKm = PolylineDistance.haversineMeters(centerLat, centerLng, dropoffLoc.getLat(), dropoffLoc.getLng()) / 1000.0;

        if (pickupDistKm > maxRadiusKm || dropoffDistKm > maxRadiusKm) {
            throw BaseDomainException.of("ride.validation.service-area-violation",
                "Pickup " + pickupDistKm + "km or dropoff " + dropoffDistKm + "km outside 25 km service area from FPT University");
        }

        var route = routingService.getRoute(
            pickupLoc.getLat(), pickupLoc.getLng(),
            dropoffLoc.getLat(), dropoffLoc.getLng()
        );

        var fareBreakdown = pricingService.quote(new PriceInput(route.distance(), null, userId));
        var quote = new Quote(
            UUID.randomUUID(),
            userId,
            pickupLoc,
            dropoffLoc,
            route.distance(),
            route.time(),
            route.polyline(),
            fareBreakdown,
            Instant.now(),
            Instant.now().plusSeconds(300) // Quote valid for 5 minutes
        );

        quoteCache.save(quote);

        return quote;
    }

    private Location findOrCreateLocation(Integer locationId, com.mssus.app.dto.ride.LatLng latLng) {
        if (locationId != null && latLng == null) {
            return locationRepository.findById(locationId)
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "Location not found with ID: " + locationId));
        }

        if (locationId == null && latLng != null) {
            return locationRepository.findByLatAndLng(latLng.latitude(), latLng.longitude())
                .orElseGet(() -> {
                    Location newLocation = new Location();
                    newLocation.setName(null);
                    newLocation.setLat(latLng.latitude());
                    newLocation.setLng(latLng.longitude());
                    newLocation.setAddress(routingService.getAddressFromCoordinates(
                        latLng.latitude(), latLng.longitude()));
                    newLocation.setCreatedAt(LocalDateTime.now());
                    newLocation.setIsPoi(false);
                    return locationRepository.save(newLocation);
                });
        }

        if (locationId != null) {
            throw BaseDomainException.of("ride.validation.invalid-location", "Provide either locationId or coordinates, not both");
        }

        throw BaseDomainException.of("ride.validation.invalid-location", "Either locationId or coordinates must be provided");
    }

    private boolean isLocationNearTarget(Location source, Location target) {
        double latDiff = Math.abs(target.getLat() - source.getLat());
        double lngDiff = Math.abs(target.getLng() - source.getLng());
        return latDiff <= LOCATION_TOLERANCE && lngDiff <= LOCATION_TOLERANCE;
    }

    private void validateRequiredLocations(Location pickupLoc, Location dropoffLoc, Location fptuLoc, Location schLoc) {
        boolean pickupIsFptu = isLocationNearTarget(pickupLoc, fptuLoc);
        boolean dropoffIsFptu = isLocationNearTarget(dropoffLoc, fptuLoc);
        boolean pickupIsSch = isLocationNearTarget(pickupLoc, schLoc);
        boolean dropoffIsSch = isLocationNearTarget(dropoffLoc, schLoc);

        if (!pickupIsFptu && !dropoffIsFptu && !pickupIsSch && !dropoffIsSch) {
            throw BaseDomainException.of("ride.validation.invalid-location",
                "Either pickup or dropoff location must be FPT University or Student Culture House");
        }
    }

    @Override
    public Quote getQuote(UUID quoteId) {
        return quoteCache.load(quoteId)
            .orElseThrow(() -> BaseDomainException.of("ride.validation.invalid-location",
                "Quote not found or expired: " + quoteId));
    }
}
