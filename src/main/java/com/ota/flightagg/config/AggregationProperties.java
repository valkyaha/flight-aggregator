package com.ota.flightagg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "ota")
public record AggregationProperties(BigDecimal markupPercentage, Connection connection) {

    public record Connection(long minMinutes, long maxMinutes) {}
}
