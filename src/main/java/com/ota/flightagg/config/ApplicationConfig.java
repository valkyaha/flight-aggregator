package com.ota.flightagg.config;

import com.ota.flightagg.application.IngestFlightsService;
import com.ota.flightagg.domain.port.in.IngestFlightsUseCase;
import com.ota.flightagg.domain.port.out.FlightProviderPort;
import com.ota.flightagg.domain.port.out.FlightRepositoryPort;
import com.ota.flightagg.domain.port.out.SearchCachePort;
import com.ota.flightagg.domain.service.AggregationService;
import com.ota.flightagg.domain.service.ConnectionValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ApplicationConfig {

    @Bean
    public IngestFlightsUseCase ingestFlightsUseCase(List<FlightProviderPort> providers,
                                                     ConnectionValidator connectionValidator,
                                                     AggregationService aggregationService,
                                                     FlightRepositoryPort repository,
                                                     SearchCachePort cache,
                                                     ProviderProperties providerProperties) {
        return new IngestFlightsService(providers, connectionValidator, aggregationService,
                repository, cache, providerProperties.deadline());
    }
}
