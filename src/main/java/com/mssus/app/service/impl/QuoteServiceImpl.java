package com.mssus.app.service.impl;

import com.mssus.app.common.exception.BaseDomainException;
import com.mssus.app.dto.request.QuoteRequest;
import com.mssus.app.pricing.PricingService;
import com.mssus.app.pricing.QuoteCache;
import com.mssus.app.pricing.model.PriceInput;
import com.mssus.app.pricing.model.Quote;
import com.mssus.app.repository.PricingConfigRepository;
import com.mssus.app.service.QuoteService;
import com.mssus.app.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {
    private final QuoteCache quoteCache;
    private final RoutingService routingService;
    private final PricingConfigRepository cfgRepo;
    private final PricingService pricingService;

    @Override
    public Quote generateQuote(QuoteRequest request, int userId) {
        var route = routingService.getRoute(request.pickup().latitude(), request.pickup().longitude(),
            request.dropoff().latitude(), request.dropoff().longitude());

        var pricingConfigId = cfgRepo.findActive(Instant.now())
            .orElseThrow(() -> BaseDomainException.of("pricing-config.not-found.resource"))
            .getPricingConfigId();

        var fareBreakdown = pricingService.quote(new PriceInput(route.distance(), route.time(), Optional.empty(), null));
        var quote = new Quote(
            UUID.randomUUID(),
            userId,
            request.pickup().latitude(),
            request.pickup().longitude(),
            request.dropoff().latitude(),
            request.dropoff().longitude(),
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

    @Override
    public Quote getQuote(UUID quoteId) {
        return quoteCache.load(quoteId)
                .orElseThrow(() -> BaseDomainException.of("ride.validation.invalid-location", 
                        "Quote not found or expired: " + quoteId));
    }
}
