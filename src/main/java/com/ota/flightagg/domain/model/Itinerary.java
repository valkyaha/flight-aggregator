package com.ota.flightagg.domain.model;

import java.util.List;
import java.util.Objects;

public record Itinerary(List<Leg> legs, Money price, Provider source) {

    public Itinerary {
        Objects.requireNonNull(legs, "legs must not be null");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(source, "source must not be null");
        legs = List.copyOf(legs);
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("Itinerary must have at least one leg");
        }
    }

    public Airport origin() {
        return legs.getFirst().from();
    }

    public Airport destination() {
        return legs.getLast().to();
    }

    public int stops() {
        return legs.size() - 1;
    }

    public ItineraryKey key() {
        return ItineraryKey.of(this);
    }
}
