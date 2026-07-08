package com.ota.flightagg.domain.port.out;

import com.ota.flightagg.domain.model.Itinerary;

import java.util.Collection;

public interface FlightRepositoryPort {

    void saveAll(Collection<Itinerary> itineraries);

    SearchPage search(SearchCriteria criteria);
}
