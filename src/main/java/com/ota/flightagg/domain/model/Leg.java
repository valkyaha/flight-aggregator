package com.ota.flightagg.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record Leg(FlightNumber flight, Airport from, Airport to, Instant departure, Instant arrival) {

    public Leg {
        Objects.requireNonNull(flight, "flight must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(departure, "departure must not be null");
        Objects.requireNonNull(arrival, "arrival must not be null");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Leg origin and destination must differ: " + from);
        }
        if (!arrival.isAfter(departure)) {
            throw new IllegalArgumentException("Leg arrival must be after departure: " + departure + " -> " + arrival);
        }
    }

    public Duration duration() {
        return Duration.between(departure, arrival);
    }
}
