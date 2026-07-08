package com.ota.flightagg.domain.port.in;

public record IngestionResult(int providersQueried,
                              int providersSucceeded,
                              int received,
                              int rejected,
                              int aggregated) {
}
