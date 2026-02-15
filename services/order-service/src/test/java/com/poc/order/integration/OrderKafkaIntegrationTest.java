package com.poc.order.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.poc.order.adapter.out.messaging.OrderEventPublisher;
import com.poc.order.application.port.out.OrderRepository;
import com.poc.order.application.port.out.PaymentPort;
import com.poc.order.application.port.out.ProductQueryPort;
import com.poc.order.domain.event.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test verifying that {@link OrderEventPublisher} correctly publishes
 * {@link OrderCreatedEvent} domain events to the {@code order.created} Kafka topic.
 *
 * <p>Uses Testcontainers to spin up a real Kafka broker and a raw
 * {@link KafkaConsumer} to verify the published message.</p>
 */
@SpringBootTest
@Testcontainers
class OrderKafkaIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderEventPublisher orderEventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock out ports not relevant to Kafka testing
    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private ProductQueryPort productQueryPort;

    @MockitoBean
    private PaymentPort paymentPort;

    @Test
    @DisplayName("OrderEventPublisher should publish OrderCreatedEvent to order.created topic")
    void shouldPublishOrderCreatedEvent() throws Exception {
        // Arrange
        OrderCreatedEvent event = new OrderCreatedEvent(
                "ord-001", "cust-001", "prod-001",
                2, "59.98", "USD", Instant.now()
        );

        // Act
        orderEventPublisher.publish(event);

        // Assert: consume from the topic and verify
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of("order.created"));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertFalse(records.isEmpty(), "Expected at least one record on order.created topic");

            var record = records.iterator().next();
            assertEquals("ord-001", record.key());

            JsonNode envelope = objectMapper.readTree(record.value());
            assertEquals("ORDER_CREATED", envelope.get("eventType").asText());
            assertEquals("order-service", envelope.get("source").asText());
            assertNotNull(envelope.get("eventId").asText());
            assertNotNull(envelope.get("timestamp").asText());
            assertNotNull(envelope.get("correlationId").asText());

            JsonNode payload = envelope.get("payload");
            assertEquals("ord-001", payload.get("orderId").asText());
            assertEquals("cust-001", payload.get("customerId").asText());
            assertEquals("prod-001", payload.get("productId").asText());
            assertEquals(2, payload.get("quantity").asInt());
            assertEquals(59.98, payload.get("totalAmount").get("amount").asDouble(), 0.001);
            assertEquals("USD", payload.get("totalAmount").get("currency").asText());
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-kafka-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}
