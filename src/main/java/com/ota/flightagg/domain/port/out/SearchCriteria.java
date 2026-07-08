package com.ota.flightagg.domain.port.out;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Carrier;

import java.math.BigDecimal;

public record SearchCriteria(Airport origin,
                             Airport destination,
                             BigDecimal maxPrice,
                             Carrier carrier,
                             Cursor cursor,
                             int limit) {

    public SearchCriteria {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
    }
}
