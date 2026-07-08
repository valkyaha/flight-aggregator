package com.ota.flightagg.domain.port.in;

import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;

public interface SearchFlightsUseCase {

    SearchPage search(SearchCriteria criteria);
}
