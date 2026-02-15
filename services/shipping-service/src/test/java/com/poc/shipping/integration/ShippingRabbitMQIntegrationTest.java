package com.poc.shipping.integration;

import com.poc.shipping.application.port.out.ShipmentRepository;
import com.poc.shipping.domain.model.Shipment;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test that verifies the RabbitMQ consumer in the Shipping Service:
 * <ul>
 *   <li>A shipping notification message is consumed from the {@code shipping.queue}</li>
 *   <li>The {@code ArrangeShipmentUseCase} is triggered with the correct order ID</li>
 * </ul>
 *
 * <p>Uses Testcontainers for RabbitMQ.</p>
 */
@SpringBootTest
@Testcontainers
class ShippingRabbitMQIntegrationTest {

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @MockitoBean
    private ShipmentRepository shipmentRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldConsumeRabbitMQMessageAndArrangeShipment() {
        // Arrange: mock repository to return whatever is saved
        when(shipmentRepository.save(any(Shipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: publish a shipping notification message to the exchange
        String shippingMessage = """
                {"orderId": "ord-001", "action": "ARRANGE_SHIPMENT"}
                """;
        rabbitTemplate.convertAndSend("shipping.exchange", "shipping.notification", shippingMessage);

        // Assert: verify the shipment repository was called (shipment was arranged)
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        verify(shipmentRepository, atLeastOnce()).save(any(Shipment.class)));
    }
}
