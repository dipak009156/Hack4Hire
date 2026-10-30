package com.quantumlock.outbox;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled relay that polls the {@code outbox} table and publishes pending events
 * to RabbitMQ with at-least-once delivery semantics (ADR-005).
 *
 * <p>Each relay cycle runs in its own transaction. The outbox row is updated to
 * {@code DELIVERED} only after the broker publish succeeds, ensuring consistency.
 * Failed publishes increment the attempt counter but leave the status {@code PENDING}
 * for retry on the next cycle.
 */
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    static final String EXCHANGE = "quantum-lock.events";

    private final OutboxRepository outboxRepository;
    private final AmqpTemplate amqpTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:5000}")
    @Transactional
    public void relay() {
        // FOR UPDATE SKIP LOCKED: allows multiple relay instances to run concurrently
        // without blocking each other; each pod picks a non-overlapping set of events.
        List<OutboxEvent> pending = outboxRepository.findPendingEventsForUpdateSkipLocked(100);
        if (pending.isEmpty()) return;

        log.debug("Outbox relay: processing {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                publishToRabbit(event);
                outboxRepository.markDelivered(event.getId());
                log.debug("Outbox event delivered id={} type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                outboxRepository.incrementAttempts(event.getId());
                log.warn("Outbox relay failed for event id={} attempts={}: {}",
                        event.getId(), event.getDeliveryAttempts() + 1, e.getMessage());
            }
        }
    }

    @CircuitBreaker(name = "broker")
    @Retry(name = "broker")
    private void publishToRabbit(OutboxEvent event) {
        String routingKey = event.getAggregateType() + "." + event.getEventType();
        amqpTemplate.convertAndSend(EXCHANGE, routingKey, event.getPayload());
    }
}

