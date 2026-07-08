package com.ota.flightagg.domain.port.out;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public record Cursor(BigDecimal price, long id) {

    private static final char SEPARATOR = ':';

    public Cursor {
        Objects.requireNonNull(price, "price must not be null");
    }

    public String encode() {
        var raw = price.toPlainString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String token) {
        Objects.requireNonNull(token, "token must not be null");
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed cursor token", e);
        }
        var separator = raw.lastIndexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("malformed cursor token");
        }
        try {
            return new Cursor(new BigDecimal(raw.substring(0, separator)), Long.parseLong(raw.substring(separator + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("malformed cursor token", e);
        }
    }
}
