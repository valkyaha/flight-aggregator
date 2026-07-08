package com.ota.mockproviders;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final NetworkSimulationInterceptor networkSimulation;

    public WebConfig(NetworkSimulationInterceptor networkSimulation) {
        this.networkSimulation = networkSimulation;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(networkSimulation)
                .addPathPatterns("/alpha/**", "/beta/**", "/gamma/**");
    }
}
