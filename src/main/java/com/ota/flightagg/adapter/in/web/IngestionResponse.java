package com.ota.flightagg.adapter.in.web;

import com.ota.flightagg.domain.port.in.IngestionResult;

public record IngestionResponse(int providersQueried,
                                int providersSucceeded,
                                int received,
                                int rejected,
                                int aggregated) {

    public static IngestionResponse from(IngestionResult result) {
        return new IngestionResponse(result.providersQueried(), result.providersSucceeded(),
                result.received(), result.rejected(), result.aggregated());
    }
}
