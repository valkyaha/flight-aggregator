package com.ota.flightagg.adapter.out.provider;

import com.ota.flightagg.config.ProviderProperties;
import com.ota.flightagg.domain.model.ParseResult;
import com.ota.flightagg.domain.model.Provider;
import com.ota.flightagg.domain.port.out.FlightProviderPort;
import com.ota.flightagg.domain.port.out.ProviderQuery;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class GammaProviderAdapter implements FlightProviderPort {

    private static final MediaType TEXT_CSV = MediaType.valueOf("text/csv");

    private final RestClient client;
    private final String path;
    private final CsvItineraryParser parser;

    public GammaProviderAdapter(RestClient.Builder builder, ProviderProperties properties, CsvItineraryParser parser) {
        this.client = builder.baseUrl(properties.gamma().baseUrl()).build();
        this.path = properties.gamma().path();
        this.parser = parser;
    }

    @Override
    public Provider provider() {
        return Provider.GAMMA;
    }

    @Override
    @Retryable(includes = {HttpServerErrorException.class, ResourceAccessException.class},
            maxRetries = ProviderHttp.RETRY_MAX_ATTEMPTS, delay = ProviderHttp.RETRY_INITIAL_DELAY_MS,
            multiplier = ProviderHttp.RETRY_BACKOFF_MULTIPLIER, maxDelay = ProviderHttp.RETRY_MAX_DELAY_MS,
            timeout = ProviderHttp.RETRY_ATTEMPT_TIMEOUT_MS, timeUnit = TimeUnit.MILLISECONDS)
    public List<ParseResult> fetch(ProviderQuery query) {
        var body = client.get()
                .uri(uri -> uri.path(path)
                        .queryParam(ProviderHttp.QUERY_ORIGIN, query.origin().iata())
                        .queryParam(ProviderHttp.QUERY_DESTINATION, query.destination().iata())
                        .queryParam(ProviderHttp.QUERY_DATE, query.departureDate())
                        .build())
                .accept(TEXT_CSV)
                .retrieve()
                .body(String.class);
        return parser.parse(body == null ? "" : body);
    }
}
