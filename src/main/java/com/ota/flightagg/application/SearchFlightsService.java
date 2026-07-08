package com.ota.flightagg.application;

import com.ota.flightagg.domain.port.in.SearchFlightsUseCase;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.SearchCachePort;
import com.ota.flightagg.domain.port.out.SearchCriteria;
import com.ota.flightagg.domain.port.out.SearchPage;
import org.springframework.stereotype.Service;

@Service
public class SearchFlightsService implements SearchFlightsUseCase {

    private final FlightRepositoryPort repository;
    private final SearchCachePort cache;

    public SearchFlightsService(FlightRepositoryPort repository, SearchCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public SearchPage search(SearchCriteria criteria) {
        return cache.get(criteria).orElseGet(() -> {
            var page = repository.search(criteria);
            cache.put(criteria, page);
            return page;
        });
    }
}
