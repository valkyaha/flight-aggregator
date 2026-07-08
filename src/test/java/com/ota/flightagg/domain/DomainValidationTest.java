package com.ota.flightagg.domain;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;
import com.ota.flightagg.domain.model.FlightNumber;
import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ItineraryKey;
import com.ota.flightagg.domain.model.Leg;
import com.ota.flightagg.domain.model.Money;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.Cursor;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.service.ConnectionRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainValidationTest {

    private static final Instant T1 = Instant.parse("2026-07-10T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-10T10:15:00Z");
    private static final Carrier IB = new Carrier("IB");

    @Test
    void moneyRejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), Currency.getInstance("EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flightNumberRejectsNonNumericAndOverlongValues() {
        assertThatThrownBy(() -> new FlightNumber(IB, "12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightNumber(IB, "AB")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legRejectsSameAirportAndNonChronologicalTimes() {
        var flight = new FlightNumber(IB, "3170");
        assertThatThrownBy(() -> new Leg(flight, new Airport("MAD"), new Airport("MAD"), T1, T2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Leg(flight, new Airport("MAD"), new Airport("LHR"), T2, T1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itineraryRejectsEmptyLegs() {
        assertThatThrownBy(() -> new Itinerary(List.of(), Money.of("10.00", "EUR"), Provider.ALPHA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itineraryKeyRejectsInconsistentComponents() {
        var flight = new FlightNumber(IB, "3170");
        assertThatThrownBy(() -> new ItineraryKey(IB, List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItineraryKey(IB, List.of(flight), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItineraryKey(IB, List.of(flight), List.of(new Airport("LHR")), List.of(T1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerQueryRejectsIdenticalOriginAndDestination() {
        assertThatThrownBy(() -> new ProviderQuery(new Airport("MAD"), new Airport("MAD"), LocalDate.parse("2026-07-10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchCriteriaRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new SearchCriteria(null, null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursorRejectsValidBase64WithoutSeparator() {
        var token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("nocolon".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> Cursor.decode(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectionRulesRejectNegativeAndInvertedBounds() {
        assertThatThrownBy(() -> new ConnectionRules(Duration.ofMinutes(-1), Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConnectionRules(Duration.ofMinutes(60), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
