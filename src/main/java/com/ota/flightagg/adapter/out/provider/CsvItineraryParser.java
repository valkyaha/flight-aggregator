package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.FlightNumber;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.Leg;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.model.RejectionReason;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class CsvItineraryParser implements ItineraryParser {

    private static final String DELIMITER = ",";
    private static final int KEEP_TRAILING_EMPTY = -1;
    private static final String HEADER_MARKER = "itineraryid";

    private static final int COL_ITINERARY_ID = 0;
    private static final int COL_CARRIER = 1;
    private static final int COL_FLIGHT_NUMBER = 2;
    private static final int COL_FROM = 3;
    private static final int COL_TO = 4;
    private static final int COL_DEPARTURE = 5;
    private static final int COL_ARRIVAL = 6;
    private static final int COL_PRICE = 7;
    private static final int COL_CURRENCY = 8;
    private static final int COLUMNS = 9;

    @Override
    public Provider provider() {
        return Provider.GAMMA;
    }

    @Override
    public List<ParseResult> parse(String rawPayload) {
        var results = new ArrayList<ParseResult>();
        var grouped = new LinkedHashMap<String, List<String[]>>();
        var lines = rawPayload.strip().lines().toList();
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i).strip();
            if (line.isEmpty() || (i == 0 && line.toLowerCase().startsWith(HEADER_MARKER))) {
                continue;
            }
            var fields = line.split(DELIMITER, KEEP_TRAILING_EMPTY);
            if (fields.length != COLUMNS) {
                results.add(new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, line));
                continue;
            }
            grouped.computeIfAbsent(fields[COL_ITINERARY_ID].strip(), k -> new ArrayList<>()).add(fields);
        }
        for (var group : grouped.values()) {
            results.add(toResult(group));
        }
        return results;
    }

    private ParseResult toResult(List<String[]> rows) {
        try {
            var legs = rows.stream()
                    .sorted(Comparator.comparing(row -> Instant.parse(row[COL_DEPARTURE].strip())))
                    .map(this::toLeg)
                    .toList();
            var price = Money.of(rows.getFirst()[COL_PRICE].strip(), rows.getFirst()[COL_CURRENCY].strip());
            return new ParseResult.Valid(new Itinerary(legs, price, Provider.GAMMA));
        } catch (RuntimeException e) {
            return new ParseResult.Invalid(RejectionReason.MALFORMED_SOURCE, e.getMessage());
        }
    }

    private Leg toLeg(String[] row) {
        var flight = new FlightNumber(new Carrier(row[COL_CARRIER].strip()), row[COL_FLIGHT_NUMBER].strip());
        return new Leg(flight, new Airport(row[COL_FROM].strip()), new Airport(row[COL_TO].strip()),
                Instant.parse(row[COL_DEPARTURE].strip()), Instant.parse(row[COL_ARRIVAL].strip()));
    }
}
