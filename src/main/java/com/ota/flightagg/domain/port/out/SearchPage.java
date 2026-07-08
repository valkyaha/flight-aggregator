package com.ota.flightagg.domain.port.out;

import com.ota.flightagg.domain.model.ItineraryView;

import java.util.List;

public record SearchPage(List<ItineraryView> items, String nextCursor) {

    public SearchPage {
        items = List.copyOf(items);
    }
}
