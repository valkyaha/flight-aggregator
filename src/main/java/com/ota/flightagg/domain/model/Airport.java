package com.ota.flightagg.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record
Airport(String iata) {

    private static final Pattern IATA = Pattern.compile("^[A-Z]{3}$");

    public Airport {
        Objects.requireNonNull(iata, "iata must not be null");
        iata = iata.strip().toUpperCase(Locale.ROOT);
        if (!IATA.matcher(iata).matches()) {
            throw new IllegalArgumentException("Invalid IATA airport code: '" + iata + "' (expected 3 letters)");
        }
    }
}
