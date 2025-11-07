package com.mssus.app.service.domain.matching.session;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisMatchingSessionRepository implements MatchingSessionRepository {

    private static final String KEY_PREFIX = "ride:matching:session:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<MatchingSessionState> find(Integer requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        String key = buildKey(requestId);
        Object value = redisTemplate.opsForValue().get(key);
        
        if (value == null) {
            log.warn("No session found in Redis for request {} (key: {})", requestId, key);
            return Optional.empty();
        }
        
        if (value instanceof MatchingSessionState state) {
            log.debug("Retrieved session for request {} - phase: {}, proposals: {}", 
                requestId, state.getPhase(), 
                state.getProposals() != null ? state.getProposals().size() : 0);
            return Optional.of(state);
        }
        
        log.error("Unexpected type in Redis for request {}: {}", requestId, value.getClass().getName());
        return Optional.empty();
    }

    @Override
    public void save(MatchingSessionState state, Duration ttl) {
        if (state == null) {
            return;
        }
        String key = buildKey(state.getRequestId());
        try {
            redisTemplate.opsForValue().set(key, state, ttl);
            log.info("Saved matching session {} to Redis - phase: {}, proposals: {}, ttl: {}", 
                state.getRequestId(), state.getPhase(), 
                state.getProposals() != null ? state.getProposals().size() : 0, 
                ttl);
        } catch (Exception e) {
            log.error("Failed to save session {} to Redis", state.getRequestId(), e);
            throw e;
        }
    }

    @Override
    public void delete(Integer requestId) {
        redisTemplate.delete(buildKey(requestId));
    }

    private String buildKey(Integer requestId) {
        return KEY_PREFIX + requestId;
    }
}
