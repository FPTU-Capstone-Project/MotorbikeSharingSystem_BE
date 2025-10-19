package com.mssus.app.service.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.QuoteRequest;
import com.mssus.app.entity.Location;
import com.mssus.app.pricing.PricingService;
import com.mssus.app.pricing.QuoteCache;
import com.mssus.app.pricing.model.PriceInput;
import com.mssus.app.pricing.model.Quote;
import com.mssus.app.repository.LocationRepository;
import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.service.QuoteService;
import com.mssus.app.service.RideMatchingService;
import com.mssus.app.service.RoutingService;
import com.mssus.app.util.PolylineDistance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {
    private final QuoteCache quoteCache;
    private final RoutingService routingService;
    private final PricingConfigRepository cfgRepo;
    private final PricingService pricingService;
    private final LocationRepository locationRepository;

    private static final double LOCATION_TOLERANCE = 0.001;

    @Override
    public Quote generateQuote(QuoteRequest request, int userId) {
        Location pickupLoc;
        Location dropoffLoc;
        boolean isPickupALocation = false;
        boolean isDropoffALocation = false;

        Location fptuLoc = locationRepository.findByLatAndLng(10.841480, 106.809844)
            .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                "FPT University location not found"));

        Location schLoc = locationRepository.findByLatAndLng(10.8753395, 106.8000331)
            .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                "Student Culture House location not found"));

        if (request.pickupLocationId() != null) {
            pickupLoc = locationRepository.findById(request.pickupLocationId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "Pickup location not found"));
            isPickupALocation = true;
        } else {
            pickupLoc = new Location();
            pickupLoc.setName("Temp pickup");
            pickupLoc.setLat(request.pickup().latitude());
            pickupLoc.setLng(request.pickup().longitude());
        }

        if (request.dropoffLocationId() != null) {
            dropoffLoc = locationRepository.findById(request.dropoffLocationId())
                .orElseThrow(() -> BaseDomainException.formatted("ride.validation.invalid-location",
                    "Dropoff location not found"));
            isDropoffALocation = true;
        } else {
            dropoffLoc = new Location();
            dropoffLoc.setName("Temp dropoff");
            dropoffLoc.setLat(request.dropoff().latitude());
            dropoffLoc.setLng(request.dropoff().longitude());
        }

        if (pickupLoc == null || dropoffLoc == null) {
            throw BaseDomainException.formatted("ride.validation.invalid-location",
                "Invalid pickup coordinates");
        }

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
                "Pickup " + pickupDistKm +"km or dropoff " + dropoffDistKm + "km outside 25 km service area from FPT University");
        }

        var route = routingService.getRoute(
            pickupLoc.getLat(), pickupLoc.getLng(),
            dropoffLoc.getLat(), dropoffLoc.getLng()
        );

        var pricingConfigId = cfgRepo.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"))
            .getPricingConfigId();

        var fareBreakdown = pricingService.quote(new PriceInput(route.distance(), null, userId));
        var quote = new Quote(
            UUID.randomUUID(),
            userId,
            isPickupALocation ? pickupLoc.getLocationId() : null,
            isDropoffALocation ? dropoffLoc.getLocationId() : null,
            pickupLoc.getLat(),
            pickupLoc.getLng(),
            dropoffLoc.getLat(),
            dropoffLoc.getLng(),
            route.distance(),
            route.time(),
            route.polyline(),
            pricingConfigId,
            fareBreakdown,
            Instant.now(),
            Instant.now().plusSeconds(300) // Quote valid for 5 minutes
            );

        quoteCache.save(quote);

        return quote;
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
