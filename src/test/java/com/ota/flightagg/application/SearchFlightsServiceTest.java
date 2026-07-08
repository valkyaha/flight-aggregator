package com.ota.flightagg.application;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.ItineraryView;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.SearchCachePort;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFlightsServiceTest {

    private static final SearchCriteria CRITERIA =
            new SearchCriteria(new Airport("MAD"), new Airport("LHR"), null, null, null, 20);

    private static SearchPage page(String source) {
        return new SearchPage(List.of(new ItineraryView(new Airport("MAD"), new Airport("LHR"),
                new Carrier("IB"), Money.of("100.00", "EUR"),
                Instant.parse("2026-07-10T08:00:00Z"), Instant.parse("2026-07-10T10:15:00Z"),
                0, Provider.valueOf(source))), null);
    }

    @Test
    void returnsCachedPageWithoutHittingRepositoryOnHit() {
        var repository = new CountingRepository(page("ALPHA"));
        var cache = new StubCache(Optional.of(page("BETA")));

        var result = new SearchFlightsService(repository, cache).search(CRITERIA);

        assertThat(result.items().getFirst().source()).isEqualTo(Provider.BETA);
        assertThat(repository.calls).isZero();
        assertThat(cache.puts).isZero();
    }

    @Test
    void loadsFromRepositoryAndPopulatesCacheOnMiss() {
        var repository = new CountingRepository(page("ALPHA"));
        var cache = new StubCache(Optional.empty());

        var result = new SearchFlightsService(repository, cache).search(CRITERIA);

        assertThat(result.items().getFirst().source()).isEqualTo(Provider.ALPHA);
        assertThat(repository.calls).isEqualTo(1);
        assertThat(cache.puts).isEqualTo(1);
    }

    private static final class CountingRepository implements FlightRepositoryPort {
        private final SearchPage page;
        private int calls;

        private CountingRepository(SearchPage page) {
            this.page = page;
        }

        @Override
        public void saveAll(java.util.Collection<com.ota.flightagg.domain.model.Itinerary> itineraries) {
        }

        @Override
        public SearchPage search(SearchCriteria criteria) {
            calls++;
            return page;
        }
    }

    private static final class StubCache implements SearchCachePort {
        private final Optional<SearchPage> hit;
        private int puts;

        private StubCache(Optional<SearchPage> hit) {
            this.hit = hit;
        }

        @Override
        public Optional<SearchPage> get(SearchCriteria criteria) {
            return hit;
        }

        @Override
        public void put(SearchCriteria criteria, SearchPage page) {
            puts++;
        }

        @Override
        public void invalidateAll() {
        }
    }
}
