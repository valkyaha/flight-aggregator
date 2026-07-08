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
public class BetaProviderAdapter implements FlightProviderPort {

    private final RestClient client;
    private final String path;
    private final XmlItineraryParser parser;

    public BetaProviderAdapter(RestClient.Builder builder, ProviderProperties properties, XmlItineraryParser parser) {
        this.client = builder.baseUrl(properties.beta().baseUrl()).build();
        this.path = properties.beta().path();
        this.parser = parser;
    }

    @Override
    public Provider provider() {
        return Provider.BETA;
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
                .accept(MediaType.APPLICATION_XML)
                .retrieve()
                .body(String.class);
        return parser.parse(body == null ? "" : body);
    }
}
