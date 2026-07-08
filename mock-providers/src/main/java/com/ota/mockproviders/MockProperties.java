package com.ota.mockproviders;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock")
public record MockProperties(Latency latency, double errorRate, int itinerariesPerQuery) {

    public record Latency(long minMs, long maxMs) {}
}
