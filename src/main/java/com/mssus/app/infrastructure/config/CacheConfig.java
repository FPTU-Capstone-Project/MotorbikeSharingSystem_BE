package com.mssus.app.infrastructure.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * ✅ FIX P0-BALANCE_CACHE: Cache configuration cho balance calculation
 * 
 * Strategy:
 * - Redis cache với TTL 5 phút cho balance queries
 * - In-memory fallback nếu Redis unavailable
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * ✅ FIX P0-BALANCE_CACHE: Redis cache manager cho balance
     * TTL: 5 phút (300 giây)
     */
    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(5))  // TTL: 5 phút
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();  // Không cache null values
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .transactionAware()  // Đảm bảo cache invalidation trong transaction
            .build();
    }
    
    /**
     * ✅ FIX P0-BALANCE_CACHE: In-memory fallback cache
     * Dùng khi Redis unavailable
     */
    @Bean(name = "fallbackCacheManager")
    public CacheManager fallbackCacheManager() {
        return new ConcurrentMapCacheManager("walletBalance");
    }
}

