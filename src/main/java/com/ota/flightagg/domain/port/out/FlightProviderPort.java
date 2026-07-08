package com.ota.flightagg.domain.port.out;

import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;

import java.util.List;

public interface FlightProviderPort {

    Provider provider();

    List<ParseResult> fetch(ProviderQuery query);
}
