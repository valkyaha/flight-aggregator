package com.ota.flightagg.domain.port.out;

import java.util.Optional;

public interface SearchCachePort {

    Optional<SearchPage> get(SearchCriteria criteria);

    void put(SearchCriteria criteria, SearchPage page);

    void invalidateAll();
}
