package com.poc.product.config;

import org.springframework.context.annotation.Configuration;

/**
 * gRPC server configuration for the Product Service.
 * <p>
 * The {@code grpc-server-spring-boot-starter} auto-configures the gRPC server
 * using properties from {@code application.yml} (e.g., {@code grpc.server.port}).
 * This configuration class serves as an extension point for any future
 * customisations such as interceptors or server builder customisers.
 */
@Configuration
public class GrpcServerConfig {
    // Auto-configuration from grpc-server-spring-boot-starter handles server setup.
    // gRPC server port is configured via grpc.server.port in application.yml.
}
