package com.poc.notification.integration;

import com.poc.notification.application.port.out.NotificationRepository;
import com.poc.notification.domain.model.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test that verifies the full messaging flow through the Notification Service:
 * <ol>
 *   <li>Kafka consumer receives a PaymentCompleted event from the {@code payment.completed} topic</li>
 *   <li>The notification is created via {@code HandlePaymentEventUseCase}</li>
 *   <li>A shipping notification message is published to RabbitMQ</li>
 * </ol>
 *
 * <p>Uses Testcontainers for both Kafka and RabbitMQ.</p>
 */
@SpringBootTest
@Testcontainers
class NotificationMessagingIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @MockitoBean
    private NotificationRepository notificationRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String PAYMENT_COMPLETED_EVENT = """
            {
              "eventId": "evt-001",
              "eventType": "PAYMENT_COMPLETED",
              "timestamp": "2026-02-15T10:00:00Z",
              "source": "payment-service",
              "correlationId": "corr-001",
              "payload": {
                "paymentId": "pay-001",
                "orderId": "ord-001",
                "amount": { "amount": 59.98, "currency": "USD" },
                "status": "COMPLETED"
              }
            }
            """;

    @Test
    void shouldConsumePaymentCompletedAndCreateNotification() {
        // Arrange: mock repository to return whatever is saved
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: send a payment.completed event to Kafka
        kafkaTemplate.send("payment.completed", "ord-001", PAYMENT_COMPLETED_EVENT);

        // Assert: verify the notification repository was called (notification was created)
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        verify(notificationRepository, atLeastOnce()).save(any(Notification.class)));
    }

    @Test
    void shouldPublishToRabbitMQAfterNotification() {
        // Arrange: mock repository to return whatever is saved
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: send a payment.completed event to Kafka
        kafkaTemplate.send("payment.completed", "ord-001", PAYMENT_COMPLETED_EVENT);

        // Assert: verify a message was published to the RabbitMQ shipping queue
        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    Object message = rabbitTemplate.receiveAndConvert("shipping.queue", 1000);
                    assertNotNull(message, "Expected a message on shipping.queue");
                    String messageStr = message.toString();
                    assertTrue(messageStr.contains("ord-001"),
                            "Message should contain orderId 'ord-001', got: " + messageStr);
                    assertTrue(messageStr.contains("ARRANGE_SHIPMENT"),
                            "Message should contain action 'ARRANGE_SHIPMENT', got: " + messageStr);
                });
    }
}
