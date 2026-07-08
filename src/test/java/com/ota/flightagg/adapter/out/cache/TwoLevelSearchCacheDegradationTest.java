package com.ota.flightagg.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ota.flightagg.config.CacheProperties;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.ItineraryView;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TwoLevelSearchCacheDegradationTest {

    private static final SearchCriteria CRITERIA =
            new SearchCriteria(new Airport("MAD"), new Airport("LHR"), null, null, null, 20);

    private static SearchPage samplePage() {
        return new SearchPage(List.of(new ItineraryView(new Airport("MAD"), new Airport("LHR"),
                new Carrier("IB"), Money.of("105.00", "EUR"),
                Instant.parse("2026-07-10T08:00:00Z"), Instant.parse("2026-07-10T10:15:00Z"),
                0, Provider.BETA)), "CUR");
    }

    private static TwoLevelSearchCache cacheWithBrokenRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(ops);
        given(ops.get(anyString())).willThrow(new RuntimeException("redis down"));
        given(redis.keys(anyString())).willThrow(new RuntimeException("redis down"));
        Cache<String, SearchPage> local = Caffeine.newBuilder().maximumSize(100).build();
        return new TwoLevelSearchCache(local, redis, new CacheProperties(Duration.ofSeconds(30), 100));
    }

    @Test
    void getReturnsEmptyInsteadOfThrowingWhenRedisIsDown() {
        var cache = cacheWithBrokenRedis();

        assertThat(cache.get(CRITERIA)).isEmpty();
    }

    @Test
    void putDoesNotThrowWhenRedisIsDownAndStillServesFromLocalCache() {
        var cache = cacheWithBrokenRedis();

        assertThatCode(() -> cache.put(CRITERIA, samplePage())).doesNotThrowAnyException();
        assertThat(cache.get(CRITERIA)).contains(samplePage());
    }

    @Test
    void invalidateAllDoesNotThrowWhenRedisIsDown() {
        var cache = cacheWithBrokenRedis();
        cache.put(CRITERIA, samplePage());

        assertThatCode(cache::invalidateAll).doesNotThrowAnyException();
        assertThat(cache.get(CRITERIA)).isEmpty();
    }
}
