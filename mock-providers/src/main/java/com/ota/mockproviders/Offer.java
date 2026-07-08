package com.ota.mockproviders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Offer(List<Segment> segments, BigDecimal basePrice) {

    public record Segment(String carrier, String flightNumber, String from, String to,
                          Instant departure, Instant arrival) {}
}
