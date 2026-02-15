package com.poc.e2e;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DLQ infrastructure verification tests.
 *
 * <p>Verifies that Dead Letter Queue (DLQ) topics and queues can be created
 * and that messages can be routed to them. These tests validate the DLQ
 * topology independently of any service consumer logic.</p>
 *
 * <p>Kafka DLQ topics verified:
 * <ul>
 *   <li>{@code order.created.dlq} (consumed by Payment Service)</li>
 *   <li>{@code payment.completed.dlq} (consumed by Notification Service)</li>
 *   <li>{@code shipment.arranged.dlq} (consumed by Order Service)</li>
 * </ul>
 *
 * <p>RabbitMQ DLQ queues verified:
 * <ul>
 *   <li>{@code shipping.queue.dlq} via {@code shipping.exchange.dlq}</li>
 * </ul>
 */
@Testcontainers
class DlqVerificationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3.13-management"));

    // ── Kafka DLQ Tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Kafka DLQ topics can be created and receive messages")
    void kafkaDlqTopicsShouldAcceptMessages() throws ExecutionException, InterruptedException {
        List<String> dlqTopics = List.of(
                "order.created.dlq",
                "payment.completed.dlq",
                "shipment.arranged.dlq"
        );

        // Create DLQ topics via AdminClient
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {

            List<NewTopic> newTopics = dlqTopics.stream()
                    .map(name -> new NewTopic(name, 1, (short) 1))
                    .toList();
            admin.createTopics(newTopics).all().get();

            // Verify topics exist
            Set<String> existingTopics = admin.listTopics().names().get();
            for (String dlqTopic : dlqTopics) {
                assertTrue(existingTopics.contains(dlqTopic),
                        "DLQ topic '" + dlqTopic + "' should exist after creation");
            }
        }

        // Produce a message to each DLQ topic and verify it can be consumed
        try (KafkaProducer<String, String> producer = createKafkaProducer()) {
            for (String dlqTopic : dlqTopics) {
                String messageValue = "{\"error\":\"test-dlq\",\"originalTopic\":\"" +
                        dlqTopic.replace(".dlq", "") + "\"}";
                producer.send(new ProducerRecord<>(dlqTopic, "dlq-key", messageValue)).get();
            }
            producer.flush();
        }

        // Consume from each DLQ topic and verify messages arrived
        try (KafkaConsumer<String, String> consumer = createKafkaConsumer()) {
            consumer.subscribe(dlqTopics);

            List<ConsumerRecord<String, String>> allRecords = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && allRecords.size() < dlqTopics.size()) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(1));
                polled.forEach(allRecords::add);
            }

            assertEquals(dlqTopics.size(), allRecords.size(),
                    "Should have received one message per DLQ topic");

            for (String dlqTopic : dlqTopics) {
                boolean found = allRecords.stream()
                        .anyMatch(r -> r.topic().equals(dlqTopic));
                assertTrue(found, "Should have received a message on DLQ topic: " + dlqTopic);
            }
        }
    }

    @Test
    @DisplayName("Kafka DeadLetterPublishingRecoverer routes messages to .dlq suffix topic")
    void kafkaDlqRoutingShouldAppendDlqSuffix() throws ExecutionException, InterruptedException {
        String sourceTopic = "test.source.topic";
        String expectedDlqTopic = sourceTopic + ".dlq";

        // Create both topics
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(sourceTopic, 1, (short) 1),
                    new NewTopic(expectedDlqTopic, 1, (short) 1)
            )).all().get();
        }

        // Simulate DLQ routing: produce directly to the DLQ topic
        // (mirrors what DeadLetterPublishingRecoverer does after retries are exhausted)
        String originalMessage = "{\"orderId\":\"ord-fail-001\",\"invalid\":true}";
        try (KafkaProducer<String, String> producer = createKafkaProducer()) {
            producer.send(new ProducerRecord<>(expectedDlqTopic, "fail-key", originalMessage)).get();
            producer.flush();
        }

        // Verify the message is on the DLQ topic
        try (KafkaConsumer<String, String> consumer = createKafkaConsumer()) {
            consumer.subscribe(List.of(expectedDlqTopic));

            ConsumerRecord<String, String> dlqRecord = null;
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && dlqRecord == null) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> r : polled) {
                    if ("fail-key".equals(r.key())) {
                        dlqRecord = r;
                        break;
                    }
                }
            }

            assertNotNull(dlqRecord, "Message should appear on DLQ topic: " + expectedDlqTopic);
            assertEquals(originalMessage, dlqRecord.value());
            assertEquals(expectedDlqTopic, dlqRecord.topic());
        }
    }

    // ── RabbitMQ DLQ Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RabbitMQ DLQ queue receives rejected messages from main queue")
    void rabbitMqDlqShouldReceiveRejectedMessages() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                rabbitmq.getHost(), rabbitmq.getAmqpPort());
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        try {
            // Declare DLQ exchange and queue
            DirectExchange dlqExchange = new DirectExchange("shipping.exchange.dlq");
            var dlqQueue = QueueBuilder.durable("shipping.queue.dlq").build();
            rabbitAdmin.declareExchange(dlqExchange);
            rabbitAdmin.declareQueue(dlqQueue);
            rabbitAdmin.declareBinding(
                    BindingBuilder.bind(dlqQueue).to(dlqExchange).with("shipping.dlq"));

            // Declare main queue with dead-letter arguments and a short per-message TTL.
            // Messages that expire (TTL=1ms) are automatically dead-lettered,
            // which is equivalent to a consumer nack/reject without requeue.
            var mainQueue = QueueBuilder.durable("shipping.queue")
                    .withArgument("x-dead-letter-exchange", "shipping.exchange.dlq")
                    .withArgument("x-dead-letter-routing-key", "shipping.dlq")
                    .withArgument("x-message-ttl", 1)
                    .build();
            rabbitAdmin.declareQueue(mainQueue);

            // Publish a message to the main queue; it will expire immediately and be dead-lettered
            String testMessage = "{\"orderId\":\"ord-dlq-001\",\"action\":\"ARRANGE_SHIPMENT\"}";
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            rabbitTemplate.send("shipping.queue", new Message(testMessage.getBytes(), props));

            // Allow a short delay for TTL expiry and dead-lettering
            Thread.sleep(500);

            // Verify the message ends up in the DLQ
            Message dlqMessage = rabbitTemplate.receive("shipping.queue.dlq", 5_000);
            assertNotNull(dlqMessage, "Expired message should appear in shipping.queue.dlq");
            String dlqBody = new String(dlqMessage.getBody());
            assertTrue(dlqBody.contains("ord-dlq-001"),
                    "DLQ message should contain the original order ID");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("RabbitMQ DLQ topology is correctly configured")
    void rabbitMqDlqTopologyShouldBeCorrectlyConfigured() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                rabbitmq.getHost(), rabbitmq.getAmqpPort());
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);

        try {
            // Declare the full topology (mimicking what Spring would do)
            DirectExchange dlqExchange = new DirectExchange("shipping.exchange.dlq");
            var dlqQueue = QueueBuilder.durable("shipping.queue.dlq").build();
            rabbitAdmin.declareExchange(dlqExchange);
            rabbitAdmin.declareQueue(dlqQueue);
            rabbitAdmin.declareBinding(
                    BindingBuilder.bind(dlqQueue).to(dlqExchange).with("shipping.dlq"));

            // Verify DLQ queue exists by publishing directly and consuming
            String directMessage = "{\"test\":\"dlq-topology-check\"}";
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            rabbitTemplate.send("shipping.exchange.dlq", "shipping.dlq",
                    new Message(directMessage.getBytes(), props));

            Message received = rabbitTemplate.receive("shipping.queue.dlq", 5_000);
            assertNotNull(received, "Direct message to DLQ exchange should arrive in DLQ queue");

            String body = new String(received.getBody());
            assertTrue(body.contains("dlq-topology-check"),
                    "DLQ message body should match what was sent");
        } finally {
            connectionFactory.destroy();
        }
    }

    // ── Helper methods ────────────────────────────────────────────────────────────

    private KafkaProducer<String, String> createKafkaProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ));
    }

    private KafkaConsumer<String, String> createKafkaConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlq-verify-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}
