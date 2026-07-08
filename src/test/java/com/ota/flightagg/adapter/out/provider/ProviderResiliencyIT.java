package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.TestcontainersConfiguration;
import com.ota.flightagg.domain.model.Airport;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.in.IngestFlightsUseCase;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProviderResiliencyIT {

    private enum Mode { OK, ERROR_500, SLOW, TRANSIENT_THEN_OK }

    private static final ProviderQuery QUERY =
            new ProviderQuery(new Airport("MAD"), new Airport("LHR"), LocalDate.parse("2026-07-10"));

    private static final Map<Provider, Mode> MODES = new EnumMap<>(Provider.class);
    private static final Map<Provider, AtomicInteger> CALLS = new EnumMap<>(Provider.class);
    private static final HttpServer SERVER;

    private static final String ALPHA_JSON = """
            [{"airline":"IB","price":"100.00","currency":"EUR","segments":[
              {"flightNumber":"3170","from":"MAD","to":"LHR",
               "departure":"2026-07-10T08:00:00Z","arrival":"2026-07-10T10:15:00Z"}]}]
            """;
    private static final String BETA_XML = """
            <GdsResponse><Itineraries><Itinerary>
              <Pricing><Amount>90.00</Amount><Currency>EUR</Currency></Pricing>
              <Segments><Segment><MarketingCarrier>IB</MarketingCarrier><FlightNumber>3170</FlightNumber>
                <Departure><Airport>MAD</Airport><DateTime>2026-07-10T08:00:00Z</DateTime></Departure>
                <Arrival><Airport>LHR</Airport><DateTime>2026-07-10T10:15:00Z</DateTime></Arrival>
              </Segment></Segments></Itinerary></Itineraries></GdsResponse>
            """;
    private static final String GAMMA_CSV = """
            itineraryId,carrier,flightNumber,from,to,departure,arrival,price,currency
            IT0,IB,3170,MAD,LHR,2026-07-10T08:00:00Z,2026-07-10T10:15:00Z,110.00,EUR
            """;

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress(0), 0);
            SERVER.setExecutor(Executors.newFixedThreadPool(8));
            SERVER.createContext("/alpha/flights", exchange -> handle(Provider.ALPHA, exchange));
            SERVER.createContext("/beta/flights", exchange -> handle(Provider.BETA, exchange));
            SERVER.createContext("/gamma/flights", exchange -> handle(Provider.GAMMA, exchange));
            SERVER.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (var provider : Provider.values()) {
            CALLS.put(provider, new AtomicInteger());
        }
    }

    @Autowired
    private IngestFlightsUseCase ingestFlights;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        var base = "http://localhost:" + SERVER.getAddress().getPort();
        registry.add("providers.alpha.base-url", () -> base);
        registry.add("providers.beta.base-url", () -> base);
        registry.add("providers.gamma.base-url", () -> base);
        registry.add("providers.deadline", () -> "2s");
    }

    @BeforeEach
    void resetServer() {
        for (var provider : Provider.values()) {
            MODES.put(provider, Mode.OK);
            CALLS.get(provider).set(0);
        }
    }

    @Test
    void aggregatesRemainingProvidersWhenOneReturns500() {
        MODES.put(Provider.BETA, Mode.ERROR_500);

        var result = ingestFlights.ingest(QUERY);

        assertThat(result.providersSucceeded()).isEqualTo(2);
        assertThat(result.aggregated()).isEqualTo(1);
    }

    @Test
    void abandonsProviderThatStallsPastTheDeadline() {
        MODES.put(Provider.GAMMA, Mode.SLOW);

        var result = ingestFlights.ingest(QUERY);

        assertThat(result.providersSucceeded()).isEqualTo(2);
        assertThat(result.aggregated()).isEqualTo(1);
    }

    @Test
    void degradesToEmptyWhenAllProvidersReturn500() {
        for (var provider : Provider.values()) {
            MODES.put(provider, Mode.ERROR_500);
        }

        var result = ingestFlights.ingest(QUERY);

        assertThat(result.providersSucceeded()).isZero();
        assertThat(result.aggregated()).isZero();
    }

    @Test
    void recoversFromTransientErrorViaRetry() {
        MODES.put(Provider.ALPHA, Mode.TRANSIENT_THEN_OK);

        var result = ingestFlights.ingest(QUERY);

        assertThat(result.providersSucceeded()).isEqualTo(3);
        assertThat(result.aggregated()).isEqualTo(1);
        assertThat(CALLS.get(Provider.ALPHA).get()).isGreaterThan(1);
    }

    private static void handle(Provider provider, HttpExchange exchange) throws IOException {
        var attempt = CALLS.get(provider).incrementAndGet();
        var mode = MODES.get(provider);
        var fail = mode == Mode.ERROR_500 || (mode == Mode.TRANSIENT_THEN_OK && attempt == 1);
        if (mode == Mode.SLOW) {
            sleep();
        }
        if (fail) {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
            return;
        }
        var body = payload(provider).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        try (var stream = exchange.getResponseBody()) {
            stream.write(body);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String payload(Provider provider) {
        return switch (provider) {
            case ALPHA -> ALPHA_JSON;
            case BETA -> BETA_XML;
            case GAMMA -> GAMMA_CSV;
        };
    }
}
