package com.ota.flightagg.domain.service;

import java.time.Duration;
import java.util.Objects;

public record ConnectionRules(Duration minConnection, Duration maxConnection) {

    public ConnectionRules {
        Objects.requireNonNull(minConnection, "minConnection must not be null");
        Objects.requireNonNull(maxConnection, "maxConnection must not be null");
        if (minConnection.isNegative()) {
            throw new IllegalArgumentException("minConnection must not be negative: " + minConnection);
        }
        if (maxConnection.compareTo(minConnection) < 0) {
            throw new IllegalArgumentException("maxConnection must be >= minConnection");
        }
    }
}
