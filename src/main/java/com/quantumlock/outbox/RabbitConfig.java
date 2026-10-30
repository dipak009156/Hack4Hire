package com.quantumlock.outbox;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration for the transactional outbox relay (ADR-005).
 *
 * <p>Declares the {@code quantum-lock.events} topic exchange. Consumers bind
 * their own queues with routing key patterns such as
 * {@code document.DOCUMENT_COMMITTED}.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange quantumLockExchange() {
        return new TopicExchange(OutboxRelay.EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

