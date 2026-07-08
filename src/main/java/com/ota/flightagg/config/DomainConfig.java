package com.ota.flightagg.config;

import com.ota.flightagg.domain.service.AggregationService;
import com.ota.flightagg.domain.service.ConnectionRules;
import com.ota.flightagg.domain.service.ConnectionValidator;
import com.ota.flightagg.domain.service.MarkupPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(AggregationProperties.class)
public class DomainConfig {

    @Bean
    public MarkupPolicy markupPolicy(AggregationProperties properties) {
        return MarkupPolicy.ofPercentage(properties.markupPercentage());
    }

    @Bean
    public ConnectionRules connectionRules(AggregationProperties properties) {
        return new ConnectionRules(
                Duration.ofMinutes(properties.connection().minMinutes()),
                Duration.ofMinutes(properties.connection().maxMinutes()));
    }

    @Bean
    public ConnectionValidator connectionValidator(ConnectionRules connectionRules) {
        return new ConnectionValidator(connectionRules);
    }

    @Bean
    public AggregationService aggregationService(MarkupPolicy markupPolicy) {
        return new AggregationService(markupPolicy);
    }
}
