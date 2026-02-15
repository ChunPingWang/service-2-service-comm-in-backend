package com.poc.payment.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fault simulation configuration for the Payment Service.
 *
 * <p>Active only when the {@code fault-simulation} Spring profile is enabled.
 * Registers a servlet filter that randomly injects latency and HTTP 500
 * errors into incoming requests, allowing integration testing of circuit
 * breaker and retry behaviour in upstream services.</p>
 *
 * <p>Configuration properties:</p>
 * <ul>
 *   <li>{@code fault.simulation.error-rate} -- probability (0.0 to 1.0) of returning a 500 error (default: 0.5)</li>
 *   <li>{@code fault.simulation.delay-ms} -- maximum additional delay in milliseconds (default: 1000)</li>
 * </ul>
 */
@Configuration
@Profile("fault-simulation")
public class FaultSimulationConfig {

    private static final Logger log = LoggerFactory.getLogger(FaultSimulationConfig.class);

    @Value("${fault.simulation.error-rate:0.5}")
    private double errorRate;

    @Value("${fault.simulation.delay-ms:1000}")
    private int delayMs;

    /**
     * Servlet filter that simulates random faults.
     *
     * <p>For each request the filter:</p>
     * <ol>
     *   <li>Adds a random delay between 500ms and {@code delayMs + 500}ms</li>
     *   <li>With probability {@code errorRate}, responds with HTTP 500 instead of forwarding the request</li>
     * </ol>
     */
    @Bean
    public Filter faultSimulationFilter() {
        log.warn("Fault simulation filter is ACTIVE: error-rate={}, delay-ms={}", errorRate, delayMs);

        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {

                // Inject random delay (500ms to delayMs + 500ms)
                int simulatedDelay = ThreadLocalRandom.current().nextInt(500, delayMs + 501);
                try {
                    Thread.sleep(simulatedDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Randomly return 500 based on error rate
                if (ThreadLocalRandom.current().nextDouble() < errorRate) {
                    log.info("Fault simulation: returning HTTP 500 (delay={}ms)", simulatedDelay);
                    if (response instanceof HttpServletResponse httpResponse) {
                        httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        httpResponse.setContentType("application/json");
                        httpResponse.getWriter().write(
                                "{\"error\":\"Simulated fault\",\"message\":\"Fault simulation is active\"}");
                        return;
                    }
                }

                log.debug("Fault simulation: passing through with delay={}ms", simulatedDelay);
                chain.doFilter(request, response);
            }
        };
    }
}
