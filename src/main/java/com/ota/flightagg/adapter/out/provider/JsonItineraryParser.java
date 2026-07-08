package com.ota.flightagg.adapter.out.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class JsonItineraryParser implements ItineraryParser {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Provider provider() {
        return Provider.ALPHA;
    }

    @Override
    public List<ParseResult> parse(String rawPayload) {
        List<AlphaItinerary> raw;
        try {
            raw = mapper.readValue(rawPayload, new TypeReference<List<AlphaItinerary>>() {});
        } catch (Exception e) {
            return List.of(new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, e.getMessage()));
        }
        return raw.stream().map(this::toResult).toList();
    }

    private ParseResult toResult(AlphaItinerary dto) {
        try {
            var legs = dto.segments().stream().map(seg -> toLeg(seg, dto.airline())).toList();
            var price = Money.of(dto.price(), dto.currency());
            return new ParseResult.Valid(new Itinerary(legs, price, Provider.ALPHA));
        } catch (RuntimeException e) {
            return new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, e.getMessage());
        }
    }

    private Leg toLeg(AlphaSegment seg, String itineraryAirline) {
        var carrierCode = seg.carrier() != null ? seg.carrier() : itineraryAirline;
        var flight = new FlightNumber(new Carrier(carrierCode), seg.flightNumber());
        return new Leg(flight, new Airport(seg.from()), new Airport(seg.to()),
                Instant.parse(seg.departure()), Instant.parse(seg.arrival()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlphaItinerary(String airline, String price, String currency, List<AlphaSegment> segments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AlphaSegment(String carrier, String flightNumber, String from, String to,
                                String departure, String arrival) {}
}
