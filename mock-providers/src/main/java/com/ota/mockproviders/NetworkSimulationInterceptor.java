package com.ota.mockproviders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class NetworkSimulationInterceptor implements HandlerInterceptor {

    private final MockProperties properties;

    public NetworkSimulationInterceptor(MockProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws InterruptedException, IOException {
        var latency = properties.latency();
        Thread.sleep(ThreadLocalRandom.current().nextLong(latency.minMs(), latency.maxMs() + 1));
        if (ThreadLocalRandom.current().nextDouble() < properties.errorRate()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "simulated provider failure");
            return false;
        }
        return true;
    }
}
