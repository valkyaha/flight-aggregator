package com.ota.flightagg.domain.port.out;

import com.ota.flightagg.domain.model.Airport;

import java.time.LocalDate;
import java.util.Objects;

public record ProviderQuery(Airport origin, Airport destination, LocalDate departureDate) {

    public ProviderQuery {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(departureDate, "departureDate must not be null");
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("origin and destination must differ: " + origin);
        }
    }
}
