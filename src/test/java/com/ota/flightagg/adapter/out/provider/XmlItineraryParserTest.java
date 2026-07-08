package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XmlItineraryParserTest {

    private final XmlItineraryParser parser = new XmlItineraryParser();

    @Test
    void parsesConnectingItineraryFromNestedXml() {
        var payload = """
                <GdsResponse>
                  <Itineraries>
                    <Itinerary>
                      <Pricing><Amount>250.00</Amount><Currency>EUR</Currency></Pricing>
                      <Segments>
                        <Segment>
                          <MarketingCarrier>IB</MarketingCarrier>
                          <FlightNumber>3170</FlightNumber>
                          <Departure><Airport>MAD</Airport><DateTime>2026-07-10T08:00:00Z</DateTime></Departure>
                          <Arrival><Airport>LHR</Airport><DateTime>2026-07-10T10:15:00Z</DateTime></Arrival>
                        </Segment>
                        <Segment>
                          <MarketingCarrier>BA</MarketingCarrier>
                          <FlightNumber>456</FlightNumber>
                          <Departure><Airport>LHR</Airport><DateTime>2026-07-10T12:00:00Z</DateTime></Departure>
                          <Arrival><Airport>JFK</Airport><DateTime>2026-07-10T15:00:00Z</DateTime></Arrival>
                        </Segment>
                      </Segments>
                    </Itinerary>
                  </Itineraries>
                </GdsResponse>
                """;

        var results = parser.parse(payload);

        assertThat(results).singleElement().isInstanceOfSatisfying(ParseResult.Valid.class, valid -> {
            var itinerary = valid.itinerary();
            assertThat(itinerary.source()).isEqualTo(Provider.BETA);
            assertThat(itinerary.origin().iata()).isEqualTo("MAD");
            assertThat(itinerary.destination().iata()).isEqualTo("JFK");
            assertThat(itinerary.stops()).isEqualTo(1);
            assertThat(itinerary.price().amount()).isEqualByComparingTo("250.00");
            assertThat(itinerary.legs().get(1).flight().designator()).isEqualTo("BA456");
        });
    }

    @Test
    void parsesMultipleItineraries() {
        var payload = """
                <GdsResponse>
                  <Itineraries>
                    <Itinerary>
                      <Pricing><Amount>100.00</Amount><Currency>EUR</Currency></Pricing>
                      <Segments><Segment>
                        <MarketingCarrier>IB</MarketingCarrier><FlightNumber>3170</FlightNumber>
                        <Departure><Airport>MAD</Airport><DateTime>2026-07-10T08:00:00Z</DateTime></Departure>
                        <Arrival><Airport>LHR</Airport><DateTime>2026-07-10T10:15:00Z</DateTime></Arrival>
                      </Segment></Segments>
                    </Itinerary>
                    <Itinerary>
                      <Pricing><Amount>110.00</Amount><Currency>EUR</Currency></Pricing>
                      <Segments><Segment>
                        <MarketingCarrier>BA</MarketingCarrier><FlightNumber>512</FlightNumber>
                        <Departure><Airport>MAD</Airport><DateTime>2026-07-10T09:00:00Z</DateTime></Departure>
                        <Arrival><Airport>LHR</Airport><DateTime>2026-07-10T11:20:00Z</DateTime></Arrival>
                      </Segment></Segments>
                    </Itinerary>
                  </Itineraries>
                </GdsResponse>
                """;

        var results = parser.parse(payload);

        assertThat(results).hasSize(2).allMatch(r -> r instanceof ParseResult.Valid);
    }

    @Test
    void rejectsMalformedXmlAsInvalid() {
        var results = parser.parse("<GdsResponse><broken>");

        assertThat(results).singleElement().isInstanceOf(ParseResult.Invalid.class);
    }
}
