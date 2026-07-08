package com.ota.flightagg.adapter.out.persistence;

import com.ota.flightagg.TestcontainersConfiguration;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ItineraryView;
import com.ota.flightagg.domain.model.Provider;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ota.flightagg.domain.Fixtures.itinerary;
import static com.ota.flightagg.domain.Fixtures.leg;
import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ItineraryRepositoryAdapterIT {

    @Autowired
    private FlightRepositoryPort repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTable() {
        jdbcTemplate.execute("TRUNCATE itinerary RESTART IDENTITY");
    }

    private static Itinerary direct(String flightNumber, String price, String departure, String arrival) {
        return itinerary(Provider.ALPHA, price, leg("IB", flightNumber, "MAD", "LHR", departure, arrival));
    }

    @Test
    void ordersResultsByPriceAscending() {
        repository.saveAll(List.of(
                direct("3170", "300.00", "2026-07-10T10:00:00Z", "2026-07-10T12:15:00Z"),
                direct("3171", "100.00", "2026-07-10T06:00:00Z", "2026-07-10T08:15:00Z"),
                direct("3172", "200.00", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z")));

        var page = repository.search(new SearchCriteria(null, null, null, null, null, 10));

        assertThat(page.items()).extracting(view -> view.price().amount())
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("200.00"), new BigDecimal("300.00"));
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void keysetPaginationCoversEveryRowInOrderWithoutOverlap() {
        repository.saveAll(List.of(
                direct("3170", "100.00", "2026-07-10T06:00:00Z", "2026-07-10T08:15:00Z"),
                direct("3171", "150.00", "2026-07-10T07:00:00Z", "2026-07-10T09:15:00Z"),
                direct("3172", "150.00", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                direct("3173", "200.00", "2026-07-10T09:00:00Z", "2026-07-10T11:15:00Z"),
                direct("3174", "300.00", "2026-07-10T10:00:00Z", "2026-07-10T12:15:00Z")));

        var collected = new ArrayList<ItineraryView>();
        Cursor cursor = null;
        var pages = 0;
        do {
            var page = repository.search(new SearchCriteria(null, null, null, null, cursor, 2));
            collected.addAll(page.items());
            cursor = page.nextCursor() == null ? null : Cursor.decode(page.nextCursor());
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(collected).hasSize(5);
        assertThat(collected).extracting(view -> view.price().amount())
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(collected).extracting(ItineraryView::departureTime).doesNotHaveDuplicates();
    }

    @Test
    void appliesOriginDestinationCarrierAndMaxPriceFilters() {
        repository.saveAll(List.of(
                itinerary(Provider.ALPHA, "100.00", leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z")),
                itinerary(Provider.ALPHA, "120.00", leg("BA", "500", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z")),
                itinerary(Provider.ALPHA, "90.00", leg("IB", "3171", "MAD", "CDG", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z")),
                itinerary(Provider.ALPHA, "80.00", leg("IB", "3172", "BCN", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"))));

        var page = repository.search(new SearchCriteria(
                new Airport("MAD"), new Airport("LHR"), new BigDecimal("110.00"), new Carrier("IB"), null, 10));

        assertThat(page.items()).singleElement().satisfies(view -> {
            assertThat(view.carrier().iata()).isEqualTo("IB");
            assertThat(view.origin().iata()).isEqualTo("MAD");
            assertThat(view.destination().iata()).isEqualTo("LHR");
            assertThat(view.price().amount()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void upsertKeepsTheCheapestPriceForTheSameItinerary() {
        var departure = "2026-07-10T08:00:00Z";
        var arrival = "2026-07-10T10:15:00Z";

        repository.saveAll(List.of(direct("3170", "200.00", departure, arrival)));
        repository.saveAll(List.of(direct("3170", "150.00", departure, arrival)));
        repository.saveAll(List.of(direct("3170", "300.00", departure, arrival)));

        var page = repository.search(new SearchCriteria(null, null, null, null, null, 10));

        assertThat(page.items()).singleElement()
                .satisfies(view -> assertThat(view.price().amount()).isEqualByComparingTo("150.00"));
    }
}
