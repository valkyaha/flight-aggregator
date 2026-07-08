package com.ota.flightagg.adapter.out.persistence;

import com.ota.flightagg.TestcontainersConfiguration;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.port.out.Cursor;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HighVolumeKeysetIT {

    private static final int ROWS = 100_000;
    private static final int PAGE_SIZE = 1_000;

    @Autowired
    private FlightRepositoryPort repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        var count = jdbcTemplate.queryForObject("SELECT count(*) FROM itinerary", Long.class);
        if (count != null && count >= ROWS) {
            return;
        }
        jdbcTemplate.execute("TRUNCATE itinerary RESTART IDENTITY");

        var sql = """
                INSERT INTO itinerary (dedup_key, origin, destination, carrier, price, currency,
                                       departure_time, arrival_time, stops, source, legs)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """;
        var departure = OffsetDateTime.of(2026, 7, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        var arrival = departure.plusHours(2);
        var random = new Random(42);
        var batch = new ArrayList<Object[]>(10_000);

        for (var i = 0; i < ROWS; i++) {
            var price = BigDecimal.valueOf(30_00L + random.nextInt(970_00)).movePointLeft(2);
            batch.add(new Object[]{
                    "K" + i, "MAD", "LHR", "IB", price, "EUR",
                    departure, arrival, 0, "ALPHA", "[]"});
            if (batch.size() == 10_000) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    @Test
    void keysetWalkVisitsEveryRowInStrictOrderWithBoundedPageLatency() {
        var seen = 0;
        var pages = 0;
        var maxPageMillis = 0L;
        Cursor cursor = null;
        BigDecimal lastPrice = null;
        long lastId = Long.MIN_VALUE;

        while (true) {
            var criteria = new SearchCriteria(new Airport("MAD"), new Airport("LHR"), null, null, cursor, PAGE_SIZE);

            var start = System.nanoTime();
            var page = repository.search(criteria);
            maxPageMillis = Math.max(maxPageMillis, (System.nanoTime() - start) / 1_000_000);

            seen += page.items().size();
            pages++;

            if (page.nextCursor() == null) {
                break;
            }
            var next = Cursor.decode(page.nextCursor());
            if (lastPrice != null) {
                var priceComparison = next.price().compareTo(lastPrice);
                assertThat(priceComparison >= 0).isTrue();
                if (priceComparison == 0) {
                    assertThat(next.id()).isGreaterThan(lastId);
                }
            }
            lastPrice = next.price();
            lastId = next.id();
            cursor = next;
        }

        assertThat(seen).isEqualTo(ROWS);
        assertThat(pages).isEqualTo(ROWS / PAGE_SIZE + 1);
        assertThat(maxPageMillis).isLessThan(1_000);
    }

    @Test
    void deepPageIsNotSlowerThanFirstPage() {
        var firstStart = System.nanoTime();
        repository.search(new SearchCriteria(new Airport("MAD"), new Airport("LHR"), null, null, null, PAGE_SIZE));
        var firstMillis = (System.nanoTime() - firstStart) / 1_000_000;

        var deepPrice = jdbcTemplate.queryForObject(
                "SELECT price FROM itinerary ORDER BY price, id OFFSET ? LIMIT 1", BigDecimal.class, ROWS - 10);
        var deepId = jdbcTemplate.queryForObject(
                "SELECT id FROM itinerary ORDER BY price, id OFFSET ? LIMIT 1", Long.class, ROWS - 10);
        var deepCursor = new Cursor(deepPrice, deepId);

        var deepStart = System.nanoTime();
        var deepPage = repository.search(
                new SearchCriteria(new Airport("MAD"), new Airport("LHR"), null, null, deepCursor, PAGE_SIZE));
        var deepMillis = (System.nanoTime() - deepStart) / 1_000_000;

        assertThat(deepPage.items()).isNotEmpty();
        assertThat(deepMillis).isLessThan(Math.max(firstMillis * 5 + 50, 500));
    }
}
