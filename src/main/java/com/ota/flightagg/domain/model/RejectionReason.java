package com.ota.flightagg.domain.model;

public enum RejectionReason {

    MALFORMED_SOURCE,

    DISCONNECTED_LEGS,

    LAYOVER_TOO_SHORT,

    LAYOVER_TOO_LONG,

    DUPLICATE_FLIGHT_NUMBER,

    DEAD_LEG,

    UNKNOWN
}
