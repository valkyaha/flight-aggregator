package com.ota.mockproviders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockProperties.class)
public class MockProvidersApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockProvidersApplication.class, args);
    }
}
