package com.ota.flightagg.adapter.in.schedule;

import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.port.in.IngestFlightsUseCase;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(name = "ingestion.startup.enabled", havingValue = "true")
public class ScheduledIngestion {

    private static final Logger log = LoggerFactory.getLogger(ScheduledIngestion.class);

    private static final List<Route> ROUTES = List.of(
            new Route("MAD", "LHR"),
            new Route("MAD", "CDG"),
            new Route("BCN", "FCO"),
            new Route("LHR", "JFK"));

    private final IngestFlightsUseCase ingestFlights;

    public ScheduledIngestion(IngestFlightsUseCase ingestFlights) {
        this.ingestFlights = ingestFlights;
    }

    @Scheduled(initialDelayString = "${ingestion.startup.initial-delay:5s}",
            fixedDelayString = "${ingestion.refresh-interval:5m}")
    public void refresh() {
        var date = LocalDate.now(ZoneOffset.UTC);
        for (var route : ROUTES) {
            try {
                var result = ingestFlights.ingest(
                        new ProviderQuery(new Airport(route.origin()), new Airport(route.destination()), date));
                log.info("ingested {}->{}: {}", route.origin(), route.destination(), result);
            } catch (RuntimeException e) {
                log.warn("ingestion failed for {}->{}: {}", route.origin(), route.destination(), e.getMessage());
            }
        }
    }

    private record Route(String origin, String destination) {}
}
