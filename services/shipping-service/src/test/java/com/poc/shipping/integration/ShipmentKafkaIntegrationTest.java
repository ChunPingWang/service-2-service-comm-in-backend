package com.poc.shipping.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.poc.shipping.adapter.out.messaging.ShipmentEventPublisher;
import com.poc.shipping.application.port.out.ShipmentRepository;
import com.poc.shipping.domain.event.ShipmentArrangedEvent;
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
 * Integration test verifying that {@link ShipmentEventPublisher} correctly publishes
 * {@link ShipmentArrangedEvent} domain events to the {@code shipment.arranged} Kafka topic.
 *
 * <p>Uses Testcontainers to spin up a real Kafka broker and a raw
 * {@link KafkaConsumer} to verify the published message.</p>
 */
@SpringBootTest
@Testcontainers
class ShipmentKafkaIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ShipmentEventPublisher shipmentEventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock out ports not relevant to Kafka testing
    @MockitoBean
    private ShipmentRepository shipmentRepository;

    @Test
    @DisplayName("ShipmentEventPublisher should publish ShipmentArrangedEvent to shipment.arranged topic")
    void shouldPublishShipmentArrangedEvent() throws Exception {
        // Arrange
        ShipmentArrangedEvent event = new ShipmentArrangedEvent(
                "shp-001", "ord-001", "TRK-20260214-001",
                "IN_TRANSIT", Instant.now()
        );

        // Act
        shipmentEventPublisher.publish(event);

        // Assert: consume from the topic and verify
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of("shipment.arranged"));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            assertFalse(records.isEmpty(), "Expected at least one record on shipment.arranged topic");

            var record = records.iterator().next();
            assertEquals("ord-001", record.key());

            JsonNode envelope = objectMapper.readTree(record.value());
            assertEquals("SHIPMENT_ARRANGED", envelope.get("eventType").asText());
            assertEquals("shipping-service", envelope.get("source").asText());
            assertNotNull(envelope.get("eventId").asText());
            assertNotNull(envelope.get("timestamp").asText());
            assertNotNull(envelope.get("correlationId").asText());

            JsonNode payload = envelope.get("payload");
            assertEquals("shp-001", payload.get("shipmentId").asText());
            assertEquals("ord-001", payload.get("orderId").asText());
            assertEquals("TRK-20260214-001", payload.get("trackingNumber").asText());
            assertEquals("IN_TRANSIT", payload.get("status").asText());
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "shipping-kafka-test",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}
