package com.poc.payment.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.poc.payment.application.port.out.PaymentRepository;
import com.poc.payment.domain.model.Money;
import com.poc.payment.domain.model.OrderId;
import com.poc.payment.domain.model.Payment;
import com.poc.payment.domain.model.PaymentId;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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

import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration test verifying the Payment Service Kafka adapters:
 * <ul>
 *   <li>{@code KafkaOrderConsumer} correctly receives order-created events and triggers payment processing</li>
 *   <li>{@code PaymentEventPublisher} correctly publishes payment-completed events</li>
 * </ul>
 *
 * <p>Uses Testcontainers to spin up a real Kafka broker.</p>
 */
@SpringBootTest
@Testcontainers
class PaymentKafkaIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("KafkaOrderConsumer should consume order.created event and process payment")
    void shouldConsumeOrderCreatedAndProcessPayment() throws Exception {
        // Arrange: stub the repository to return a completed payment
        Payment pendingPayment = Payment.create(
                new PaymentId("pay-test-001"),
                new OrderId("ord-001"),
                new Money(new BigDecimal("59.98"), "USD")
        );
        Payment completedPayment = pendingPayment.complete();

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(completedPayment);

        // Build an order.created event envelope
        String orderCreatedEvent = objectMapper.writeValueAsString(Map.of(
                "eventId", "evt-001",
                "eventType", "ORDER_CREATED",
                "timestamp", Instant.now().toString(),
                "source", "order-service",
                "correlationId", "corr-001",
                "payload", Map.of(
                        "orderId", "ord-001",
                        "customerId", "cust-001",
                        "productId", "prod-001",
                        "quantity", 2,
                        "totalAmount", Map.of(
                                "amount", 59.98,
                                "currency", "USD"
                        )
                )
        ));

        // Act: produce the event to order.created topic
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>("order.created", "ord-001", orderCreatedEvent)).get();
            producer.flush();
        }

        // Assert: verify the repository was called (payment was processed)
        // Use timeout to allow for async consumer processing
        verify(paymentRepository, timeout(10000).atLeast(2)).save(any(Payment.class));
    }

    @Test
    @DisplayName("PaymentEventPublisher should publish PaymentCompletedEvent to payment.completed topic")
    void shouldPublishPaymentCompletedEvent() throws Exception {
        // Arrange: stub the repository to return a completed payment
        Payment pendingPayment = Payment.create(
                new PaymentId("pay-test-002"),
                new OrderId("ord-002"),
                new Money(new BigDecimal("100.00"), "USD")
        );
        Payment completedPayment = pendingPayment.complete();

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(completedPayment);

        // Build an order.created event envelope that will trigger payment processing
        // which in turn should publish a payment.completed event
        String orderCreatedEvent = objectMapper.writeValueAsString(Map.of(
                "eventId", "evt-002",
                "eventType", "ORDER_CREATED",
                "timestamp", Instant.now().toString(),
                "source", "order-service",
                "correlationId", "corr-002",
                "payload", Map.of(
                        "orderId", "ord-002",
                        "customerId", "cust-002",
                        "productId", "prod-002",
                        "quantity", 1,
                        "totalAmount", Map.of(
                                "amount", 100.00,
                                "currency", "USD"
                        )
                )
        ));

        // Act: produce to order.created which triggers the whole chain
        try (KafkaProducer<String, String> producer = createProducer()) {
            producer.send(new ProducerRecord<>("order.created", "ord-002", orderCreatedEvent)).get();
            producer.flush();
        }

        // Assert: consume from payment.completed topic and find record with key ord-002
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of("payment.completed"));

            List<ConsumerRecord<String, String>> allRecords = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(1));
                polled.forEach(allRecords::add);
                if (allRecords.stream().anyMatch(r -> "ord-002".equals(r.key()))) {
                    break;
                }
            }

            var record = allRecords.stream()
                    .filter(r -> "ord-002".equals(r.key()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(record, "Expected a record with key 'ord-002' on payment.completed topic");
            assertEquals("ord-002", record.key());

            JsonNode envelope = objectMapper.readTree(record.value());
            assertEquals("PAYMENT_COMPLETED", envelope.get("eventType").asText());
            assertEquals("payment-service", envelope.get("source").asText());
            assertNotNull(envelope.get("eventId").asText());
            assertNotNull(envelope.get("timestamp").asText());
            assertNotNull(envelope.get("correlationId").asText());

            JsonNode payload = envelope.get("payload");
            assertNotNull(payload.get("paymentId").asText());
            assertEquals("ord-002", payload.get("orderId").asText());
            assertEquals(100.0, payload.get("amount").get("amount").asDouble(), 0.001);
            assertEquals("USD", payload.get("amount").get("currency").asText());
            assertEquals("COMPLETED", payload.get("status").asText());
        }
    }

    private KafkaProducer<String, String> createProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ));
    }

    private KafkaConsumer<String, String> createConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "payment-kafka-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}
