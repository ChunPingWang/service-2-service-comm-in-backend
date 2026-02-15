package com.poc.order.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for the Order Service.
 *
 * <p>Configures common tags on all metrics emitted by this service,
 * enabling consistent filtering and grouping in Prometheus and Grafana.
 * Spring Boot 4 auto-configures most OpenTelemetry integration via
 * Micrometer, so this class only adds service-level metadata.</p>
 */
@Configuration
public class ObservabilityConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags(
                        "service", applicationName,
                        "environment", activeProfile
                );
    }
}
