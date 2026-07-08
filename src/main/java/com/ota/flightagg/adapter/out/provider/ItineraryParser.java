package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;

import java.util.List;

public interface ItineraryParser {

    Provider provider();

    List<ParseResult> parse(String rawPayload);
}
