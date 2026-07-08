package com.ota.mockproviders;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

@Component
public class FlightGenerator {

    private static final List<String> CARRIERS = List.of("IB", "BA", "AF", "LH", "U2");
    private static final List<String> HUBS = List.of("AMS", "FRA", "CDG");

    private static final int MIN_FLIGHT_NUMBER = 100;
    private static final int FLIGHT_NUMBER_RANGE = 8900;
    private static final int EARLIEST_DEPARTURE_HOUR = 5;
    private static final int DEPARTURE_HOUR_WINDOW = 16;
    private static final int MINUTE_STEP = 5;
    private static final int MINUTE_STEP_COUNT = 12;
    private static final int MIN_BASE_PRICE = 50;
    private static final int BASE_PRICE_RANGE = 400;
    private static final int PRICE_SCALE = 2;
    private static final int CONNECTION_EVERY_NTH = 5;
    private static final int MIN_LEG_MINUTES = 60;
    private static final int LEG_MINUTES_RANGE = 120;
    private static final int MIN_DIRECT_MINUTES = 90;
    private static final int DIRECT_MINUTES_RANGE = 180;

    public List<Offer> generate(String origin, String destination, LocalDate date, int count) {
        var random = new Random(Objects.hash(origin, destination, date));
        var midnight = date.atStartOfDay(ZoneOffset.UTC);
        var offers = new ArrayList<Offer>(count);

        for (var i = 0; i < count; i++) {
            var carrier = CARRIERS.get(random.nextInt(CARRIERS.size()));
            var flightNumber = flightNumber(random);
            var departure = midnight.plusHours(EARLIEST_DEPARTURE_HOUR + random.nextInt(DEPARTURE_HOUR_WINDOW))
                    .plusMinutes(random.nextInt(MINUTE_STEP_COUNT) * (long) MINUTE_STEP).toInstant();
            var basePrice = BigDecimal.valueOf(MIN_BASE_PRICE + random.nextInt(BASE_PRICE_RANGE)).setScale(PRICE_SCALE);

            var hubs = HUBS.stream().filter(h -> !h.equals(origin) && !h.equals(destination)).toList();
            if (i % CONNECTION_EVERY_NTH == 0 && !hubs.isEmpty()) {
                var hub = hubs.get(random.nextInt(hubs.size()));
                var firstArrival = departure.plus(legMinutes(random));
                var secondDeparture = firstArrival.plus(legMinutes(random));
                var secondArrival = secondDeparture.plus(legMinutes(random));
                var secondCarrier = CARRIERS.get(random.nextInt(CARRIERS.size()));
                offers.add(new Offer(List.of(
                        new Offer.Segment(carrier, flightNumber, origin, hub, departure, firstArrival),
                        new Offer.Segment(secondCarrier, flightNumber(random),
                                hub, destination, secondDeparture, secondArrival)),
                        basePrice));
            } else {
                var arrival = departure.plus(Duration.ofMinutes(MIN_DIRECT_MINUTES + random.nextInt(DIRECT_MINUTES_RANGE)));
                offers.add(new Offer(List.of(
                        new Offer.Segment(carrier, flightNumber, origin, destination, departure, arrival)),
                        basePrice));
            }
        }
        return offers;
    }

    private static String flightNumber(Random random) {
        return String.valueOf(MIN_FLIGHT_NUMBER + random.nextInt(FLIGHT_NUMBER_RANGE));
    }

    private static Duration legMinutes(Random random) {
        return Duration.ofMinutes(MIN_LEG_MINUTES + random.nextInt(LEG_MINUTES_RANGE));
    }
}
