package com.ota.flightagg.domain.model;

import java.time.Instant;

public record ItineraryView(Airport origin,
                            Airport destination,
                            Carrier carrier,
                            Money price,
                            Instant departureTime,
                            Instant arrivalTime,
                            int stops,
                            Provider source) {
}
