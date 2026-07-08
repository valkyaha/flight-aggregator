package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonItineraryParserTest {

    private final JsonItineraryParser parser = new JsonItineraryParser();

    @Test
    void parsesDirectItinerary() {
        var payload = """
                [
                  {
                    "airline": "IB",
                    "price": "123.45",
                    "currency": "EUR",
                    "segments": [
                      {"flightNumber": "3170", "from": "MAD", "to": "LHR",
                       "departure": "2026-07-10T08:00:00Z", "arrival": "2026-07-10T10:15:00Z"}
                    ]
                  }
                ]
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOfSatisfying(ParseResult.Valid.class, valid -> {
            var itinerary = valid.itinerary();
            assertThat(itinerary.source()).isEqualTo(Provider.ALPHA);
            assertThat(itinerary.origin().iata()).isEqualTo("MAD");
            assertThat(itinerary.destination().iata()).isEqualTo("LHR");
            assertThat(itinerary.stops()).isZero();
            assertThat(itinerary.price().amount()).isEqualByComparingTo("123.45");
            assertThat(itinerary.legs().getFirst().flight().designator()).isEqualTo("IB3170");
        });
    }

    @Test
    void parsesConnectingItineraryWithPerSegmentCarrier() {
        var payload = """
                [
                  {
                    "airline": "IB", "price": "250.00", "currency": "EUR",
                    "segments": [
                      {"flightNumber": "3170", "from": "MAD", "to": "LHR",
                       "departure": "2026-07-10T08:00:00Z", "arrival": "2026-07-10T10:15:00Z"},
                      {"carrier": "BA", "flightNumber": "456", "from": "LHR", "to": "JFK",
                       "departure": "2026-07-10T12:00:00Z", "arrival": "2026-07-10T15:00:00Z"}
                    ]
                  }
                ]
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOfSatisfying(ParseResult.Valid.class, valid -> {
            var itinerary = valid.itinerary();
            assertThat(itinerary.stops()).isEqualTo(1);
            assertThat(itinerary.destination().iata()).isEqualTo("JFK");
            assertThat(itinerary.legs().get(1).flight().designator()).isEqualTo("BA456");
        });
    }

    @Test
    void rejectsMalformedJsonAsInvalid() {
        var results = parser.parse("this is not json");

        assertThat(results).singleElement().isInstanceOf(ParseResult.Invalid.class);
    }

    @Test
    void rejectsBadAirportCodeAsInvalid() {
        var payload = """
                [
                  {"airline": "IB", "price": "100.00", "currency": "EUR",
                   "segments": [{"flightNumber": "3170", "from": "MADRID", "to": "LHR",
                    "departure": "2026-07-10T08:00:00Z", "arrival": "2026-07-10T10:15:00Z"}]}
                ]
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOf(ParseResult.Invalid.class);
    }

    @Test
    void rejectsArrivalBeforeDepartureAsInvalid() {
        var payload = """
                [
                  {"airline": "IB", "price": "100.00", "currency": "EUR",
                   "segments": [{"flightNumber": "3170", "from": "MAD", "to": "LHR",
                    "departure": "2026-07-10T10:15:00Z", "arrival": "2026-07-10T08:00:00Z"}]}
                ]
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOf(ParseResult.Invalid.class);
    }

    @Test
    void returnsEmptyForEmptyList() {
        assertThat(parser.parse("[]")).isEmpty();
    }
}
