package com.ota.flightagg.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record FlightNumber(Carrier carrier, String number) {

    private static final Pattern NUMBER = Pattern.compile("^\\d{1,4}$");

    public FlightNumber {
        Objects.requireNonNull(carrier, "carrier must not be null");
        Objects.requireNonNull(number, "number must not be null");
        number = number.strip();
        if (!NUMBER.matcher(number).matches()) {
            throw new IllegalArgumentException("Invalid flight number: '" + number + "' (expected 1-4 digits)");
        }
    }

    public String designator() {
        return carrier.iata() + number;
    }
}
