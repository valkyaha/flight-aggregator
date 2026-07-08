package com.ota.flightagg.application;

import com.ota.flightagg.domain.model.Itinerary;
import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.in.IngestFlightsUseCase;
import com.ota.flightagg.domain.port.in.IngestionResult;
import com.ota.flightagg.domain.port.out.FlightProviderPort;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import com.ota.flightagg.domain.port.out.SearchCachePort;
import com.ota.flightagg.domain.service.AggregationService;
import com.ota.flightagg.domain.service.ConnectionValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.TimeoutException;
import java.util.stream.Stream;

public class IngestFlightsService implements IngestFlightsUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestFlightsService.class);
    private static final String SCOPE_NAME = "provider-fan-out";

    private final List<FlightProviderPort> providers;
    private final ConnectionValidator connectionValidator;
    private final AggregationService aggregationService;
    private final FlightRepositoryPort repository;
    private final SearchCachePort cache;
    private final Duration deadline;

    public IngestFlightsService(List<FlightProviderPort> providers,
                                ConnectionValidator connectionValidator,
                                AggregationService aggregationService,
                                FlightRepositoryPort repository,
                                SearchCachePort cache,
                                Duration deadline) {
        this.providers = List.copyOf(providers);
        this.connectionValidator = Objects.requireNonNull(connectionValidator, "connectionValidator must not be null");
        this.aggregationService = Objects.requireNonNull(aggregationService, "aggregationService must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
    }

    @Override
    public IngestionResult ingest(ProviderQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        var fetches = fanOut(query);
        warnOnDroppedProviders(query, fetches);
        var received = fetches.stream().mapToInt(fetch -> fetch.results().size()).sum();

        var parsed = fetches.stream()
                .flatMap(fetch -> fetch.results().stream())
                .flatMap(result -> result instanceof ParseResult.Valid valid
                        ? Stream.of(valid.itinerary()) : Stream.<Itinerary>empty())
                .toList();

        var connected = parsed.stream()
                .flatMap(itinerary -> connectionValidator.validate(itinerary) instanceof ParseResult.Valid valid
                        ? Stream.of(valid.itinerary()) : Stream.<Itinerary>empty())
                .toList();

        var aggregated = aggregationService.aggregate(connected);
        repository.saveAll(aggregated);
        cache.invalidateAll();

        return new IngestionResult(providers.size(), fetches.size(), received,
                received - connected.size(), aggregated.size());
    }

    private void warnOnDroppedProviders(ProviderQuery query, List<ProviderFetch> fetches) {
        if (fetches.size() == providers.size()) {
            return;
        }
        var returned = EnumSet.noneOf(Provider.class);
        fetches.forEach(fetch -> returned.add(fetch.provider()));
        var dropped = providers.stream()
                .map(FlightProviderPort::provider)
                .filter(provider -> !returned.contains(provider))
                .toList();
        log.warn("provider fan-out incomplete for {}->{}: {}/{} returned, dropped {}",
                query.origin().iata(), query.destination().iata(), fetches.size(), providers.size(), dropped);
    }

    private List<ProviderFetch> fanOut(ProviderQuery query) {
        var completed = new ConcurrentLinkedQueue<ProviderFetch>();
        try (var scope = StructuredTaskScope.open(
                Joiner.<ProviderFetch>awaitAll(),
                configuration -> configuration.withTimeout(deadline).withName(SCOPE_NAME))) {

            for (var provider : providers) {
                scope.fork(() -> {
                    var fetch = new ProviderFetch(provider.provider(), provider.fetch(query));
                    completed.add(fetch);
                    return fetch;
                });
            }

            try {
                scope.join();
            } catch (TimeoutException deadlineExceeded) {
                return List.copyOf(completed);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ingestion interrupted", interrupted);
        }
        return List.copyOf(completed);
    }

    private record ProviderFetch(Provider provider, List<ParseResult> results) {}
}
