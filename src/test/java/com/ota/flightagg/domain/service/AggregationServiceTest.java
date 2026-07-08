package com.ota.flightagg.domain.service;

import com.ota.flightagg.domain.model.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.ota.flightagg.domain.Fixtures.itinerary;
import static com.ota.flightagg.domain.Fixtures.leg;
import static org.assertj.core.api.Assertions.assertThat;

class AggregationServiceTest {

    private final AggregationService aggregation =
            new AggregationService(MarkupPolicy.ofPercentage(new BigDecimal("5")));

    @Test
    void keepsCheapestOfIdenticalItinerariesAndAppliesMarkup() {
        var alpha = itinerary(Provider.ALPHA, "120.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));
        var beta = itinerary(Provider.BETA, "100.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));
        var gamma = itinerary(Provider.GAMMA, "110.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));

        var result = aggregation.aggregate(List.of(alpha, beta, gamma));

        assertThat(result).singleElement().satisfies(it -> {
            assertThat(it.source()).isEqualTo(Provider.BETA);
            assertThat(it.price().amount()).isEqualByComparingTo("105.00");
        });
    }

    @Test
    void treatsDifferentDepartureTimesAsDistinctItineraries() {
        var morning = itinerary(Provider.ALPHA, "100.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));
        var evening = itinerary(Provider.ALPHA, "90.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T20:00:00Z", "2026-07-10T22:15:00Z"));

        var result = aggregation.aggregate(List.of(morning, evening));

        assertThat(result).hasSize(2);
    }

    @Test
    void appliesMarkupToEveryResult() {
        var it = itinerary(Provider.ALPHA, "200.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));

        var result = aggregation.aggregate(List.of(it));

        assertThat(result).singleElement()
                .satisfies(r -> assertThat(r.price().amount()).isEqualByComparingTo("210.00"));
    }

    @Test
    void returnsEmptyForNoCandidates() {
        assertThat(aggregation.aggregate(List.of())).isEmpty();
    }

    @Test
    void breaksPriceTiesDeterministicallyByProviderOrder() {
        var gamma = itinerary(Provider.GAMMA, "100.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));
        var alpha = itinerary(Provider.ALPHA, "100.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));

        assertThat(aggregation.aggregate(List.of(gamma, alpha)))
                .singleElement()
                .satisfies(it -> assertThat(it.source()).isEqualTo(Provider.ALPHA));
        assertThat(aggregation.aggregate(List.of(alpha, gamma)))
                .singleElement()
                .satisfies(it -> assertThat(it.source()).isEqualTo(Provider.ALPHA));
    }
}
