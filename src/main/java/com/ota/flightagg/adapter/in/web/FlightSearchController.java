package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.port.in.SearchFlightsUseCase;
import com.ota.flightagg.domain.port.out.Cursor;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/flights")
public class FlightSearchController {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SIZE = "20";

    private final SearchFlightsUseCase searchFlights;

    public FlightSearchController(SearchFlightsUseCase searchFlights) {
        this.searchFlights = searchFlights;
    }

    @GetMapping
    public FlightSearchResponse search(@RequestParam(required = false) String origin,
                                       @RequestParam(required = false) String destination,
                                       @RequestParam(required = false) BigDecimal maxPrice,
                                       @RequestParam(required = false) String carrier,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        var criteria = new SearchCriteria(
                origin == null ? null : new Airport(origin),
                destination == null ? null : new Airport(destination),
                maxPrice,
                carrier == null ? null : new Carrier(carrier),
                cursor == null || cursor.isBlank() ? null : Cursor.decode(cursor),
                Math.clamp(size, MIN_SIZE, MAX_SIZE));
        return FlightSearchResponse.from(searchFlights.search(criteria));
    }
}
