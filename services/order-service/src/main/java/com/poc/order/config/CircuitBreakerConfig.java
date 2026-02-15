package com.poc.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit breaker configuration for the Order Service.
 *
 * <p>Resilience4j is auto-configured from {@code application.yml} via the
 * {@code resilience4j-spring-boot3} starter. This configuration class
 * registers event listeners for circuit breaker state transitions, providing
 * visibility into circuit breaker activity through application logs.</p>
 *
 * <p>The actual circuit breaker parameters (sliding window, failure rate
 * threshold, wait duration, etc.) are defined in {@code application.yml}
 * under the {@code resilience4j.circuitbreaker} key.</p>
 */
@Configuration
public class CircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfig.class);

    /**
     * Registers event consumers on all circuit breakers so that state transitions
     * and error events are logged.
     */
    public CircuitBreakerConfig(CircuitBreakerRegistry circuitBreakerRegistry) {
        circuitBreakerRegistry.getEventPublisher()
                .onEntryAdded(event -> {
                    var cb = event.getAddedEntry();
                    cb.getEventPublisher()
                            .onStateTransition(stateEvent ->
                                    log.warn("CircuitBreaker '{}' state transition: {}",
                                            cb.getName(), stateEvent.getStateTransition()))
                            .onError(errorEvent ->
                                    log.debug("CircuitBreaker '{}' recorded error: {}",
                                            cb.getName(), errorEvent.getThrowable().getMessage()))
                            .onCallNotPermitted(notPermittedEvent ->
                                    log.warn("CircuitBreaker '{}' rejected call (state: OPEN)",
                                            cb.getName()));
                });
    }
}
