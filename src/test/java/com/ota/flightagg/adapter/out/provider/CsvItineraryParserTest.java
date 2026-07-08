package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvItineraryParserTest {

    private final CsvItineraryParser parser = new CsvItineraryParser();

    @Test
    void parsesDirectItineraryAndSkipsHeader() {
        var payload = """
                itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency
                IT1,IB,3170,MAD,LHR,2026-07-10T08:00:00Z,2026-07-10T10:15:00Z,99.90,EUR
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOfSatisfying(ParseResult.Valid.class, valid -> {
            var itinerary = valid.itinerary();
            assertThat(itinerary.source()).isEqualTo(Provider.GAMMA);
            assertThat(itinerary.stops()).isZero();
            assertThat(itinerary.price().amount()).isEqualByComparingTo("99.90");
        });
    }

    @Test
    void groupsRowsByItineraryAndOrdersLegsByDeparture() {
        var payload = """
                itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency
                IT2,BA,456,LHR,JFK,2026-07-10T12:00:00Z,2026-07-10T15:00:00Z,120.00,EUR
                IT2,IB,3170,MAD,LHR,2026-07-10T08:00:00Z,2026-07-10T10:15:00Z,120.00,EUR
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOfSatisfying(ParseResult.Valid.class, valid -> {
            var itinerary = valid.itinerary();
            assertThat(itinerary.stops()).isEqualTo(1);
            assertThat(itinerary.origin().iata()).isEqualTo("MAD");
            assertThat(itinerary.destination().iata()).isEqualTo("JFK");
            assertThat(itinerary.legs().getFirst().flight().designator()).isEqualTo("IB3170");
        });
    }

    @Test
    void rejectsRowWithWrongColumnCountAsInvalid() {
        var payload = """
                itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency
                IT3,IB,3170,MAD,LHR
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOf(ParseResult.Invalid.class);
    }

    @Test
    void keepsValidItinerariesWhenOneRowIsMalformed() {
        var payload = """
                itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency
                BAD,IB,3170,MAD,LHR
                IT4,IB,3170,MAD,LHR,2026-07-10T08:00:00Z,2026-07-10T10:15:00Z,80.00,EUR
                """;

        var results = parser.parse(payload);

        assertThat(results).hasSize(2);
        assertThat(results).anyMatch(r -> r instanceof ParseResult.Invalid);
        assertThat(results).anyMatch(r -> r instanceof ParseResult.Valid);
    }
}
