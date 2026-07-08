package com.ota.flightagg.adapter.out.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.FlightNumber;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.Leg;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.model.RejectionReason;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class XmlItineraryParser implements ItineraryParser {

    private final XmlMapper mapper = new XmlMapper();

    @Override
    public Provider provider() {
        return Provider.BETA;
    }

    @Override
    public List<ParseResult> parse(String rawPayload) {
        BetaResponse response;
        try {
            response = mapper.readValue(rawPayload, BetaResponse.class);
        } catch (Exception e) {
            return List.of(new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, e.getMessage()));
        }
        if (response.itineraries() == null || response.itineraries().items() == null) {
            return List.of();
        }
        return response.itineraries().items().stream().map(this::toResult).toList();
    }

    private ParseResult toResult(BetaItinerary dto) {
        try {
            var legs = dto.segments().items().stream().map(this::toLeg).toList();
            var price = Money.of(dto.pricing().amount(), dto.pricing().currency());
            return new ParseResult.Valid(new Itinerary(legs, price, Provider.BETA));
        } catch (RuntimeException e) {
            return new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, e.getMessage());
        }
    }

    private Leg toLeg(BetaSegment seg) {
        var flight = new FlightNumber(new Carrier(seg.carrier()), seg.flightNumber());
        return new Leg(flight,
                new Airport(seg.departure().airport()), new Airport(seg.arrival().airport()),
                Instant.parse(seg.departure().dateTime()), Instant.parse(seg.arrival().dateTime()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JacksonXmlRootElement(localName = "GdsResponse")
    private record BetaResponse(
            @JacksonXmlProperty(localName = "Itineraries") Itineraries itineraries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Itineraries(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "Itinerary")
            List<BetaItinerary> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BetaItinerary(
            @JacksonXmlProperty(localName = "Pricing") BetaPricing pricing,
            @JacksonXmlProperty(localName = "Segments") Segments segments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Segments(
            @JacksonXmlElementWrapper(useWrapping = false)
            @JacksonXmlProperty(localName = "Segment")
            List<BetaSegment> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BetaPricing(
            @JacksonXmlProperty(localName = "Amount") String amount,
            @JacksonXmlProperty(localName = "Currency") String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BetaSegment(
            @JacksonXmlProperty(localName = "MarketingCarrier") String carrier,
            @JacksonXmlProperty(localName = "FlightNumber") String flightNumber,
            @JacksonXmlProperty(localName = "Departure") BetaEndpoint departure,
            @JacksonXmlProperty(localName = "Arrival") BetaEndpoint arrival) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BetaEndpoint(
            @JacksonXmlProperty(localName = "Airport") String airport,
            @JacksonXmlProperty(localName = "DateTime") String dateTime) {}
}
