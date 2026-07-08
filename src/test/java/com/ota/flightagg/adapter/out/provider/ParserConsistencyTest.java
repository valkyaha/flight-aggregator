package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserConsistencyTest {

    private static final String ALPHA_JSON = """
            [
              {"airline": "IB", "price": "123.45", "currency": "EUR",
               "segments": [{"flightNumber": "3170", "from": "MAD", "to": "LHR",
                "departure": "2026-07-10T08:00:00Z", "arrival": "2026-07-10T10:15:00Z"}]}
            ]
            """;

    private static final String BETA_XML = """
            <GdsResponse><Itineraries><Itinerary>
              <Pricing><Amount>140.00</Amount><Currency>EUR</Currency></Pricing>
              <Segments><Segment>
                <MarketingCarrier>IB</MarketingCarrier><FlightNumber>3170</FlightNumber>
                <Departure><Airport>MAD</Airport><DateTime>2026-07-10T08:00:00Z</DateTime></Departure>
                <Arrival><Airport>LHR</Airport><DateTime>2026-07-10T10:15:00Z</DateTime></Arrival>
              </Segment></Segments>
            </Itinerary></Itineraries></GdsResponse>
            """;

    private static final String GAMMA_CSV = """
            itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency
            IT9,IB,3170,MAD,LHR,2026-07-10T08:00:00Z,2026-07-10T10:15:00Z,99.90,EUR
            """;

    @Test
    void sameFlightFromEveryProviderYieldsSameDeduplicationKey() {
        var alpha = only(new JsonItineraryParser().parse(ALPHA_JSON));
        var beta = only(new XmlItineraryParser().parse(BETA_XML));
        var gamma = only(new CsvItineraryParser().parse(GAMMA_CSV));

        assertThat(alpha.key())
                .isEqualTo(beta.key())
                .isEqualTo(gamma.key());

        assertThat(alpha.price().amount()).isNotEqualByComparingTo(gamma.price().amount());
    }

    private static Itinerary only(List<ParseResult> results) {
        assertThat(results).singleElement().isInstanceOf(ParseResult.Valid.class);
        return ((ParseResult.Valid) results.getFirst()).itinerary();
    }
}
