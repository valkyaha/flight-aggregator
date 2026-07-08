package com.ota.flightagg.domain.service;

import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.RejectionReason;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;

public final class ConnectionValidator {

    private final ConnectionRules rules;

    public ConnectionValidator(ConnectionRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules must not be null");
    }

    public ParseResult validate(Itinerary itinerary) {
        Objects.requireNonNull(itinerary, "itinerary must not be null");
        var legs = itinerary.legs();

        var seen = new HashSet<String>();
        for (var leg : legs) {
            if (!seen.add(leg.flight().designator())) {
                return new ParseResult.Invalid(RejectionReason.DUPLICATE_FLIGHT_NUMBER,
                        "repeated flight " + leg.flight().designator());
            }
        }

        for (var i = 0; i < legs.size() - 1; i++) {
            var current = legs.get(i);
            var next = legs.get(i + 1);

            if (!current.to().equals(next.from())) {
                return new ParseResult.Invalid(RejectionReason.DISCONNECTED_LEGS,
                        current.to().iata() + " -> " + next.from().iata());
            }
            if (next.departure().isBefore(current.arrival())) {
                return new ParseResult.DeadLeg(RejectionReason.DEAD_LEG,
                        "departs " + next.departure() + " before arrival " + current.arrival());
            }
            var layover = Duration.between(current.arrival(), next.departure());
            if (layover.compareTo(rules.minConnection()) < 0) {
                return new ParseResult.Invalid(RejectionReason.LAYOVER_TOO_SHORT,
                        "layover " + layover + " below minimum " + rules.minConnection());
            }
            if (layover.compareTo(rules.maxConnection()) > 0) {
                return new ParseResult.Invalid(RejectionReason.LAYOVER_TOO_LONG,
                        "layover " + layover + " above maximum " + rules.maxConnection());
            }
        }

        return new ParseResult.Valid(itinerary);
    }
}
