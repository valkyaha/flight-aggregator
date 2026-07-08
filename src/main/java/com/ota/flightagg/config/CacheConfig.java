package com.ota.flightagg.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.ota.flightagg.domain.port.out.SearchPage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    private static final String CACHE_METRIC_NAME = "flight_search_cache";

    @Bean
    public Cache<String, SearchPage> searchCache(CacheProperties properties, MeterRegistry meterRegistry) {
        Cache<String, SearchPage> cache = Caffeine.newBuilder()
                .maximumSize(properties.maxSize())
                .expireAfterWrite(properties.ttl())
                .recordStats()
                .build();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, CACHE_METRIC_NAME);
        return cache;
    }
}
