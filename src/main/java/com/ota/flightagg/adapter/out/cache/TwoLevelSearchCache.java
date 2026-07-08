package com.ota.flightagg.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.ota.flightagg.config.CacheProperties;
import com.ota.flightagg.domain.port.out.SearchCachePort;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;

@Component
public class TwoLevelSearchCache implements SearchCachePort {

    private static final Logger log = LoggerFactory.getLogger(TwoLevelSearchCache.class);
    private static final String PREFIX = "flightsearch::";
    private static final String SCAN_PATTERN = PREFIX + "*";
    private static final int SCAN_BATCH_SIZE = 500;
    private static final String KEY_SEPARATOR = "|";
    private static final String ABSENT = "*";

    private final Cache<String, SearchPage> local;
    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    public TwoLevelSearchCache(Cache<String, SearchPage> searchCache, StringRedisTemplate redis, CacheProperties properties) {
        this.local = searchCache;
        this.redis = redis;
        this.ttl = properties.ttl();
    }

    @Override
    public Optional<SearchPage> get(SearchCriteria criteria) {
        var key = key(criteria);
        var localHit = local.getIfPresent(key);
        if (localHit != null) {
            return Optional.of(localHit);
        }
        try {
            var json = redis.opsForValue().get(key);
            if (json != null) {
                var page = mapper.readValue(json, SearchPage.class);
                local.put(key, page);
                return Optional.of(page);
            }
        } catch (Exception e) {
            log.warn("search cache L2 (redis) read failed, degrading to database: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void put(SearchCriteria criteria, SearchPage page) {
        var key = key(criteria);
        local.put(key, page);
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(page), ttl);
        } catch (Exception e) {
            log.warn("search cache L2 (redis) write failed: {}", e.getMessage());
        }
    }

    @Override
    public void invalidateAll() {
        local.invalidateAll();
        try {
            var keys = new ArrayList<String>();
            try (var cursor = redis.scan(ScanOptions.scanOptions().match(SCAN_PATTERN).count(SCAN_BATCH_SIZE).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.warn("search cache L2 (redis) invalidation failed: {}", e.getMessage());
        }
    }

    private static String key(SearchCriteria criteria) {
        return PREFIX
                + value(criteria.origin() == null ? null : criteria.origin().iata()) + KEY_SEPARATOR
                + value(criteria.destination() == null ? null : criteria.destination().iata()) + KEY_SEPARATOR
                + value(criteria.maxPrice() == null ? null : criteria.maxPrice().toPlainString()) + KEY_SEPARATOR
                + value(criteria.carrier() == null ? null : criteria.carrier().iata()) + KEY_SEPARATOR
                + value(criteria.cursor() == null ? null : criteria.cursor().encode()) + KEY_SEPARATOR
                + criteria.limit();
    }

    private static String value(String raw) {
        return raw == null ? ABSENT : raw;
    }
}
