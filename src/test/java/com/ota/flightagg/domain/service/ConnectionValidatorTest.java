package com.ota.flightagg.domain.service;

import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.model.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.ota.flightagg.domain.Fixtures.itinerary;
import static com.ota.flightagg.domain.Fixtures.leg;
import static org.assertj.core.api.Assertions.assertThat;

class ConnectionValidatorTest {

    private final ConnectionValidator validator =
            new ConnectionValidator(new ConnectionRules(Duration.ofMinutes(30), Duration.ofMinutes(1440)));

    @Test
    void acceptsDirectFlight() {
        var it = itinerary(Provider.ALPHA, "100.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"));

        assertThat(validator.validate(it)).isInstanceOf(ParseResult.Valid.class);
    }

    @Test
    void acceptsWellFormedConnection() {
        var it = itinerary(Provider.ALPHA, "250.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("BA", "456", "LHR", "JFK", "2026-07-10T12:00:00Z", "2026-07-10T15:00:00Z"));

        assertThat(validator.validate(it)).isInstanceOf(ParseResult.Valid.class);
    }

    @Test
    void rejectsGeographicallyDisconnectedLegs() {
        var it = itinerary(Provider.ALPHA, "250.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("BA", "456", "CDG", "JFK", "2026-07-10T12:00:00Z", "2026-07-10T15:00:00Z"));

        assertThat(validator.validate(it)).isInstanceOfSatisfying(ParseResult.Invalid.class,
                invalid -> assertThat(invalid.reason()).isEqualTo(RejectionReason.DISCONNECTED_LEGS));
    }

    @Test
    void rejectsConnectionThatDepartsBeforeArrivalAsDeadLeg() {
        var it = itinerary(Provider.ALPHA, "250.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("BA", "456", "LHR", "JFK", "2026-07-10T09:00:00Z", "2026-07-10T12:00:00Z"));

        assertThat(validator.validate(it)).isInstanceOfSatisfying(ParseResult.DeadLeg.class,
                deadLeg -> assertThat(deadLeg.reason()).isEqualTo(RejectionReason.DEAD_LEG));
    }

    @Test
    void rejectsLayoverBelowMinimum() {
        var it = itinerary(Provider.ALPHA, "250.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("BA", "456", "LHR", "JFK", "2026-07-10T10:30:00Z", "2026-07-10T13:00:00Z"));

        assertThat(validator.validate(it)).isInstanceOfSatisfying(ParseResult.Invalid.class,
                invalid -> assertThat(invalid.reason()).isEqualTo(RejectionReason.LAYOVER_TOO_SHORT));
    }

    @Test
    void rejectsLayoverAboveMaximum() {
        var it = itinerary(Provider.ALPHA, "250.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("BA", "456", "LHR", "JFK", "2026-07-12T12:00:00Z", "2026-07-12T15:00:00Z"));

        assertThat(validator.validate(it)).isInstanceOfSatisfying(ParseResult.Invalid.class,
                invalid -> assertThat(invalid.reason()).isEqualTo(RejectionReason.LAYOVER_TOO_LONG));
    }

    @Test
    void rejectsDuplicateFlightNumberWithinItinerary() {
        var it = itinerary(Provider.ALPHA, "250.00",
                leg("IB", "3170", "MAD", "LHR", "2026-07-10T08:00:00Z", "2026-07-10T10:15:00Z"),
                leg("IB", "3170", "LHR", "JFK", "2026-07-10T12:00:00Z", "2026-07-10T15:00:00Z"));

        assertThat(validator.validate(it)).isInstanceOfSatisfying(ParseResult.Invalid.class,
                invalid -> assertThat(invalid.reason()).isEqualTo(RejectionReason.DUPLICATE_FLIGHT_NUMBER));
    }
}
