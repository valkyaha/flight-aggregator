package com.ota.flightagg.domain;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.FlightNumber;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.Leg;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;

import java.time.Instant;
import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    public static Money eur(String amount) {
        return Money.of(amount, "EUR");
    }

    public static Leg leg(String carrier, String number, String from, String to, String departure, String arrival) {
        return new Leg(new FlightNumber(new Carrier(carrier), number),
                new Airport(from), new Airport(to), Instant.parse(departure), Instant.parse(arrival));
    }

    public static Itinerary itinerary(Provider source, String price, Leg... legs) {
        return new Itinerary(List.of(legs), eur(price), source);
    }
}
