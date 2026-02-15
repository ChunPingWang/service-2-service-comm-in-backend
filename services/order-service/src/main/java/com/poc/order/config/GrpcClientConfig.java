package com.poc.order.config;

import org.springframework.context.annotation.Configuration;

/**
 * gRPC client configuration for the Order Service.
 * <p>
 * For this PoC, the {@code grpc-client-spring-boot-starter} handles most of the
 * configuration via {@code application.yml} properties. This class serves as a
 * placeholder for any programmatic customisation that may be needed later
 * (e.g., custom interceptors, deadline policies, retry configuration).
 */
@Configuration
public class GrpcClientConfig {
    // grpc-client-spring-boot-starter auto-configures channels and stubs
    // based on grpc.client.* properties in application.yml.
}
