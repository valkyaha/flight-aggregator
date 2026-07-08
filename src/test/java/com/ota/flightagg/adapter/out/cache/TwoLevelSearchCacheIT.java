package com.ota.flightagg.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ota.flightagg.TestcontainersConfiguration;
import com.ota.flightagg.config.CacheProperties;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.ItineraryView;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TwoLevelSearchCacheIT {

    private static final SearchCriteria CRITERIA =
            new SearchCriteria(new Airport("MAD"), new Airport("LHR"), null, new Carrier("IB"), null, 20);

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        var keys = redis.keys("flightsearch::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private TwoLevelSearchCache newCache() {
        Cache<String, SearchPage> local = Caffeine.newBuilder().maximumSize(100).build();
        return new TwoLevelSearchCache(local, redis, new CacheProperties(Duration.ofSeconds(30), 100));
    }

    private static SearchPage samplePage() {
        return new SearchPage(List.of(new ItineraryView(new Airport("MAD"), new Airport("LHR"),
                new Carrier("IB"), Money.of("105.00", "EUR"),
                Instant.parse("2026-07-10T08:00:00Z"), Instant.parse("2026-07-10T10:15:00Z"),
                0, Provider.BETA)), "CUR");
    }

    @Test
    void sharesEntriesAcrossInstancesThroughRedis() {
        var writer = newCache();
        var reader = newCache();

        writer.put(CRITERIA, samplePage());
        var fromReader = reader.get(CRITERIA);

        assertThat(fromReader).contains(samplePage());
    }

    @Test
    void invalidateAllRemovesEntriesFromRedis() {
        var writer = newCache();
        writer.put(CRITERIA, samplePage());

        writer.invalidateAll();

        assertThat(newCache().get(CRITERIA)).isEmpty();
    }
}
