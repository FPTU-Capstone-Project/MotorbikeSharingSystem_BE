package com.mssus.app.service.pricing;

import com.mssus.app.service.pricing.model.Quote;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class QuoteCache {
    private final ConcurrentMap<UUID, Quote> cache = new ConcurrentHashMap<>();

    public void save(Quote q) {
        if (q.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Cannot cache an already expired quote");
        }

        cache.put(q.quoteId(), q);
    }

    public Optional<Quote> load(UUID id) {
        var q = cache.get(id);
        if (q == null) return Optional.empty();
        if (q.expiresAt().isBefore(Instant.now())) {
            cache.remove(id);
            return Optional.empty();
        }
        return Optional.of(q);
    }

    public Optional<Quote> loadByRiderId(int riderId) {
        return cache.values().stream()
            .filter(q -> q.riderId() == riderId)
            .filter(q -> q.expiresAt().isAfter(Instant.now()))
            .findFirst();
    }

    public void evict(UUID id) {
        cache.remove(id);
    }
}

