package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.domain.port.out.SearchPage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FlightSearchResponse(List<Item> items, String nextCursor) {

    public record Item(String origin,
                       String destination,
                       String carrier,
                       BigDecimal price,
                       String currency,
                       Instant departureTime,
                       Instant arrivalTime,
                       int stops,
                       String source) {}

    public static FlightSearchResponse from(SearchPage page) {
        var items = page.items().stream()
                .map(view -> new Item(
                        view.origin().iata(),
                        view.destination().iata(),
                        view.carrier().iata(),
                        view.price().amount(),
                        view.price().currency().getCurrencyCode(),
                        view.departureTime(),
                        view.arrivalTime(),
                        view.stops(),
                        view.source().name()))
                .toList();
        return new FlightSearchResponse(items, page.nextCursor());
    }
}
