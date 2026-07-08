package com.ota.flightagg.application;

import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.model.RejectionReason;
import com.ota.flightagg.domain.port.in.IngestionResult;
import com.ota.flightagg.domain.port.out.FlightProviderPort;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import com.ota.flightagg.domain.service.AggregationService;
import com.ota.flightagg.domain.service.ConnectionRules;
import com.ota.flightagg.domain.service.ConnectionValidator;
import com.ota.flightagg.domain.service.MarkupPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.ota.flightagg.domain.Fixtures.itinerary;
import static com.ota.flightagg.domain.Fixtures.leg;
import static org.assertj.core.api.Assertions.assertThat;

class IngestFlightsServiceTest {

    private static final ProviderQuery QUERY =
            new ProviderQuery(new com.ota.flightagg.domain.model.Airport("MAD"),
                    new com.ota.flightagg.domain.model.Airport("LHR"), LocalDate.parse("2026-07-10"));

    private final ConnectionValidator validator =
            new ConnectionValidator(new ConnectionRules(Duration.ofMinutes(30), Duration.ofMinutes(1440)));
    private final AggregationService aggregation =
            new AggregationService(MarkupPolicy.ofPercentage(new BigDecimal("5")));
    private final RecordingRepository repository = new RecordingRepository();
    private final RecordingCache cache = new RecordingCache();

    private static ParseResult valid(Provider provider, String price) {
        return new ParseResult.Valid(itinerary(provider, price,
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z")));
    }

    private IngestFlightsService service(Duration deadline, FlightProviderPort... providers) {
        return new IngestFlightsService(List.of(providers), validator, aggregation, repository, cache, deadline);
    }

    @Test
    void deduplicatesAcrossProvidersAndPersistsCheapestWithMarkup() {
        var result = service(Duration.ofSeconds(2),
                new FakeProvider(Provider.ALPHA, () -> List.of(valid(Provider.ALPHA, "120.00"))),
                new FakeProvider(Provider.BETA, () -> List.of(valid(Provider.BETA, "100.00"))),
                new FakeProvider(Provider.GAMMA, () -> List.of(valid(Provider.GAMMA, "110.00"))))
                .ingest(QUERY);

        assertThat(result.providersSucceeded()).isEqualTo(3);
        assertThat(result.aggregated()).isEqualTo(1);
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(repository.saved).singleElement().satisfies(it -> {
            assertThat(it.source()).isEqualTo(Provider.BETA);
            assertThat(it.price().amount()).isEqualByComparingTo("105.00");
        });
    }

    @Test
    void stillAggregatesWhenOneProviderThrows() {
        var result = service(Duration.ofSeconds(2),
                new FakeProvider(Provider.ALPHA, () -> List.of(valid(Provider.ALPHA, "120.00"))),
                new FakeProvider(Provider.BETA, () -> { throw new RuntimeException("provider down"); }),
                new FakeProvider(Provider.GAMMA, () -> List.of(valid(Provider.GAMMA, "110.00"))))
                .ingest(QUERY);

        assertThat(result.providersSucceeded()).isEqualTo(2);
        assertThat(result.aggregated()).isEqualTo(1);
        assertThat(repository.saved).singleElement().satisfies(it -> {
            assertThat(it.source()).isEqualTo(Provider.GAMMA);
            assertThat(it.price().amount()).isEqualByComparingTo("115.50");
        });
    }

    @Test
    void abandonsProviderThatExceedsDeadline() {
        var result = service(Duration.ofMillis(200),
                new FakeProvider(Provider.ALPHA, () -> List.of(valid(Provider.ALPHA, "120.00"))),
                new FakeProvider(Provider.BETA, () -> sleepThenReturn(List.of(valid(Provider.BETA, "100.00")))),
                new FakeProvider(Provider.GAMMA, () -> List.of(valid(Provider.GAMMA, "110.00"))))
                .ingest(QUERY);

        assertThat(result.providersSucceeded()).isEqualTo(2);
        assertThat(repository.saved).singleElement().satisfies(it -> {
            assertThat(it.source()).isEqualTo(Provider.GAMMA);
            assertThat(it.price().amount()).isEqualByComparingTo("115.50");
        });
    }

    @Test
    void degradesToEmptyWhenAllProvidersFail() {
        var result = service(Duration.ofSeconds(2),
                new FakeProvider(Provider.ALPHA, () -> { throw new RuntimeException("down"); }),
                new FakeProvider(Provider.BETA, () -> { throw new RuntimeException("down"); }),
                new FakeProvider(Provider.GAMMA, () -> { throw new RuntimeException("down"); }))
                .ingest(QUERY);

        assertThat(result.providersSucceeded()).isZero();
        assertThat(result.aggregated()).isZero();
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void countsMalformedAndInvalidConnectionsAsRejected() {
        var disconnected = new ParseResult.Valid(itinerary(Provider.ALPHA, "200.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("BA", "456", "CDG", "JFK", "2026-07-10T12:00:00Z", "2026-07-10T15:00:00Z")));
        var malformed = new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, "bad row");

        var result = service(Duration.ofSeconds(2),
                new FakeProvider(Provider.ALPHA,
                        () -> List.of(valid(Provider.ALPHA, "120.00"), malformed, disconnected)))
                .ingest(QUERY);

        assertThat(result.received()).isEqualTo(3);
        assertThat(result.rejected()).isEqualTo(2);
        assertThat(result.aggregated()).isEqualTo(1);
    }

    private static <T> T sleepThenReturn(T value) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return value;
    }

    private record FakeProvider(Provider provider, Supplier<List<ParseResult>> behavior) implements FlightProviderPort {
        @Override
        public Provider provider() {
            return provider;
        }

        @Override
        public List<ParseResult> fetch(ProviderQuery query) {
            return behavior.get();
        }
    }

    private static final class RecordingRepository implements FlightRepositoryPort {
        private final List<Itinerary> saved = new ArrayList<>();

        @Override
        public void saveAll(Collection<Itinerary> itineraries) {
            saved.addAll(itineraries);
        }

        @Override
        public SearchPage search(SearchCriteria criteria) {
            return new SearchPage(List.of(), null);
        }
    }

    private static final class RecordingCache implements com.ota.flightagg.domain.port.out.SearchCachePort {
        private int invalidations;

        @Override
        public java.util.Optional<SearchPage> get(SearchCriteria criteria) {
            return java.util.Optional.empty();
        }

        @Override
        public void put(SearchCriteria criteria, SearchPage page) {
        }

        @Override
        public void invalidateAll() {
            invalidations++;
        }
    }
}
