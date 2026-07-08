package com.ota.flightagg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cache.search")
public record CacheProperties(Duration ttl, long maxSize) {
}
