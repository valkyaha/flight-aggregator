package com.ota.flightagg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "providers")
public record ProviderProperties(Duration deadline,
                                 Duration connectTimeout,
                                 Duration readTimeout,
                                 Endpoint alpha,
                                 Endpoint beta,
                                 Endpoint gamma) {

    public record Endpoint(String baseUrl, String path) {}
}
