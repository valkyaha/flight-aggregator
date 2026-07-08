package com.ota.flightagg.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public record ItineraryKey(Carrier airline,
                           List<FlightNumber> flightNumbers,
                           List<Airport> connections,
                           List<Instant> departures) {

    public ItineraryKey {
        Objects.requireNonNull(airline, "airline must not be null");
        Objects.requireNonNull(flightNumbers, "flightNumbers must not be null");
        Objects.requireNonNull(connections, "connections must not be null");
        Objects.requireNonNull(departures, "departures must not be null");
        flightNumbers = List.copyOf(flightNumbers);
        connections = List.copyOf(connections);
        departures = List.copyOf(departures);
        if (flightNumbers.isEmpty()) {
            throw new IllegalArgumentException("flightNumbers must not be empty");
        }
        if (departures.size() != flightNumbers.size()) {
            throw new IllegalArgumentException("departures must have one entry per flight number");
        }
        if (connections.size() != flightNumbers.size() - 1) {
            throw new IllegalArgumentException("connections must have one entry per leg boundary");
        }
    }

    public static ItineraryKey of(Itinerary itinerary) {
        Objects.requireNonNull(itinerary, "itinerary must not be null");
        var legs = itinerary.legs();
        var airline = legs.getFirst().flight().carrier();
        var flightNumbers = legs.stream().map(Leg::flight).toList();
        var connections = legs.subList(0, legs.size() - 1).stream().map(Leg::to).toList();
        var departures = legs.stream()
                .map(leg -> leg.departure().truncatedTo(ChronoUnit.MINUTES))
                .toList();
        return new ItineraryKey(airline, flightNumbers, connections, departures);
    }
}
