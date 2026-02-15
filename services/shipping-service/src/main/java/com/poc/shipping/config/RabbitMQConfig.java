package com.poc.shipping.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for the Shipping Service.
 *
 * <p>Declares the exchange, queue, and binding needed to receive
 * shipping notification messages from the Notification Service.</p>
 *
 * <p>Includes Dead Letter Queue (DLQ) support. Messages that are rejected
 * or fail processing are routed to {@code shipping.queue.dlq} via the
 * {@code shipping.exchange.dlq} dead letter exchange.</p>
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange shippingExchange() {
        return new TopicExchange("shipping.exchange");
    }

    /**
     * Main shipping queue configured with dead-letter routing.
     *
     * <p>Failed/rejected messages are automatically routed to {@code shipping.exchange.dlq}
     * with routing key {@code shipping.dlq}.</p>
     */
    @Bean
    public Queue shippingQueue() {
        return QueueBuilder.durable("shipping.queue")
                .withArgument("x-dead-letter-exchange", "shipping.exchange.dlq")
                .withArgument("x-dead-letter-routing-key", "shipping.dlq")
                .build();
    }

    @Bean
    public Binding shippingBinding(Queue shippingQueue, TopicExchange shippingExchange) {
        return BindingBuilder.bind(shippingQueue).to(shippingExchange).with("shipping.notification");
    }

    // ── Dead Letter Queue ─────────────────────────────────────────────────────────

    @Bean
    public DirectExchange shippingDlqExchange() {
        return new DirectExchange("shipping.exchange.dlq");
    }

    @Bean
    public Queue shippingDlqQueue() {
        return QueueBuilder.durable("shipping.queue.dlq").build();
    }

    @Bean
    public Binding shippingDlqBinding(Queue shippingDlqQueue, DirectExchange shippingDlqExchange) {
        return BindingBuilder.bind(shippingDlqQueue).to(shippingDlqExchange).with("shipping.dlq");
    }

    // ── Message Converter ─────────────────────────────────────────────────────────

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
