package com.ota.flightagg.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Carrier(String iata) {

    private static final Pattern IATA = Pattern.compile("^[A-Z0-9]{2}$");

    public Carrier {
        Objects.requireNonNull(iata, "iata must not be null");
        iata = iata.strip().toUpperCase(Locale.ROOT);
        if (!IATA.matcher(iata).matches()) {
            throw new IllegalArgumentException("Invalid IATA airline code: '" + iata + "' (expected 2 alphanumerics)");
        }
    }
}
