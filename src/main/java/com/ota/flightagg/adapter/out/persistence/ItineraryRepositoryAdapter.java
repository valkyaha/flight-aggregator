package com.ota.flightagg.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.FlightNumber;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ItineraryKey;
import com.ota.flightagg.domain.model.ItineraryView;
import com.ota.flightagg.domain.model.Leg;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.Cursor;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ItineraryRepositoryAdapter implements FlightRepositoryPort {

    private static final String SEGMENT_SEPARATOR = "#";
    private static final String LIST_SEPARATOR = ",";

    private static final String UPSERT = """
            INSERT INTO itinerary (dedup_key, origin, destination, carrier, price, currency,
                                   departure_time, arrival_time, stops, source, legs)
            VALUES (:dedupKey, :origin, :destination, :carrier, :price, :currency,
                    :departureTime, :arrivalTime, :stops, :source, CAST(:legs AS jsonb))
            ON CONFLICT (dedup_key) DO UPDATE SET
                price = EXCLUDED.price,
                currency = EXCLUDED.currency,
                departure_time = EXCLUDED.departure_time,
                arrival_time = EXCLUDED.arrival_time,
                stops = EXCLUDED.stops,
                source = EXCLUDED.source,
                legs = EXCLUDED.legs,
                ingested_at = now()
            WHERE EXCLUDED.price < itinerary.price
            """;

    private static final String SEARCH = """
            SELECT id, origin, destination, carrier, price, currency,
                   departure_time, arrival_time, stops, source
            FROM itinerary
            WHERE (CAST(:origin AS text) IS NULL OR origin = :origin)
              AND (CAST(:destination AS text) IS NULL OR destination = :destination)
              AND (CAST(:carrier AS text) IS NULL OR carrier = :carrier)
              AND (CAST(:maxPrice AS numeric) IS NULL OR price <= :maxPrice)
              AND (CAST(:cursorPrice AS numeric) IS NULL
                   OR (price, id) > (CAST(:cursorPrice AS numeric), CAST(:cursorId AS bigint)))
            ORDER BY price, id
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ItineraryRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveAll(Collection<Itinerary> itineraries) {
        if (itineraries.isEmpty()) {
            return;
        }
        var batch = itineraries.stream().map(this::toParameters).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT, batch);
    }

    @Override
    public SearchPage search(SearchCriteria criteria) {
        var params = new MapSqlParameterSource()
                .addValue("origin", iataOrNull(criteria.origin()))
                .addValue("destination", iataOrNull(criteria.destination()))
                .addValue("carrier", criteria.carrier() == null ? null : criteria.carrier().iata())
                .addValue("maxPrice", criteria.maxPrice())
                .addValue("cursorPrice", criteria.cursor() == null ? null : criteria.cursor().price())
                .addValue("cursorId", criteria.cursor() == null ? null : criteria.cursor().id())
                .addValue("limit", criteria.limit());

        var rows = jdbc.query(SEARCH, params, ROW_MAPPER);
        var items = rows.stream().map(PricedRow::view).toList();
        var nextCursor = rows.size() == criteria.limit()
                ? new Cursor(rows.getLast().price(), rows.getLast().id()).encode()
                : null;
        return new SearchPage(items, nextCursor);
    }

    private SqlParameterSource toParameters(Itinerary itinerary) {
        var first = itinerary.legs().getFirst();
        var last = itinerary.legs().getLast();
        return new MapSqlParameterSource()
                .addValue("dedupKey", dedupKey(itinerary.key()))
                .addValue("origin", itinerary.origin().iata())
                .addValue("destination", itinerary.destination().iata())
                .addValue("carrier", itinerary.key().airline().iata())
                .addValue("price", itinerary.price().amount())
                .addValue("currency", itinerary.price().currency().getCurrencyCode())
                .addValue("departureTime", OffsetDateTime.ofInstant(first.departure(), ZoneOffset.UTC))
                .addValue("arrivalTime", OffsetDateTime.ofInstant(last.arrival(), ZoneOffset.UTC))
                .addValue("stops", itinerary.stops())
                .addValue("source", itinerary.source().name())
                .addValue("legs", serializeLegs(itinerary.legs()));
    }

    private String serializeLegs(List<Leg> legs) {
        var rows = legs.stream()
                .map(leg -> new LegRow(leg.flight().carrier().iata(), leg.flight().number(),
                        leg.from().iata(), leg.to().iata(),
                        leg.departure().toString(), leg.arrival().toString()))
                .toList();
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize legs", e);
        }
    }

    private static String iataOrNull(Airport airport) {
        return airport == null ? null : airport.iata();
    }

    private static String dedupKey(ItineraryKey key) {
        var flights = key.flightNumbers().stream().map(FlightNumber::designator).collect(Collectors.joining(LIST_SEPARATOR));
        var connections = key.connections().stream().map(Airport::iata).collect(Collectors.joining(LIST_SEPARATOR));
        var departures = key.departures().stream().map(Instant::toString).collect(Collectors.joining(LIST_SEPARATOR));
        return String.join(SEGMENT_SEPARATOR, key.airline().iata(), flights, connections, departures);
    }

    private static final RowMapper<PricedRow> ROW_MAPPER = (rs, rowNum) -> new PricedRow(
            rs.getLong("id"),
            rs.getBigDecimal("price"),
            new ItineraryView(
                    new Airport(rs.getString("origin")),
                    new Airport(rs.getString("destination")),
                    new Carrier(rs.getString("carrier")),
                    new Money(rs.getBigDecimal("price"), Currency.getInstance(rs.getString("currency"))),
                    rs.getObject("departure_time", OffsetDateTime.class).toInstant(),
                    rs.getObject("arrival_time", OffsetDateTime.class).toInstant(),
                    rs.getInt("stops"),
                    Provider.valueOf(rs.getString("source"))));

    private record PricedRow(long id, BigDecimal price, ItineraryView view) {}

    private record LegRow(String carrier, String number, String from, String to, String departure, String arrival) {}
}
