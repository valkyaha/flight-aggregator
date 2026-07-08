package com.ota.flightagg.domain.model;

import java.util.Objects;

public sealed interface ParseResult permits ParseResult.Valid, ParseResult.DeadLeg, ParseResult.Invalid {

    record Valid(Itinerary itinerary) implements ParseResult {
        public Valid {
            Objects.requireNonNull(itinerary, "itinerary must not be null");
        }
    }

    record DeadLeg(RejectionReason reason, String detail) implements ParseResult {
        public DeadLeg {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    record Invalid(RejectionReason reason, String detail) implements ParseResult {
        public Invalid {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
