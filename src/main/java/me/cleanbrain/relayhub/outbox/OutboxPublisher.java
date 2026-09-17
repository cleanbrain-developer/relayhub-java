package me.cleanbrain.relayhub.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.delivery.DeliveryTaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls PENDING {@link OutboxEvent} rows and publishes one Kafka message per row to
 * {@link #TOPIC}. See specs/003-kafka-outbox/spec.md ("OutboxPublisher").
 */
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    public static final String TOPIC = "relayhub.delivery-tasks";

    // Arbitrary but fixed, distinct from DlqAutoReplayScheduler's own lock key — see
    // OutboxEventRepository.tryAdvisoryXactLock's Javadoc for why this one is safe to scope to a
    // single transaction while that one isn't.
    private static final long LOCK_KEY = 4_921_733_002L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, DeliveryTaskMessage> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final Environment environment;

    @Scheduled(fixedDelay = 200)
    @Transactional
    public void publishPending() {
        // pg_try_advisory_xact_lock doesn't exist on H2 — the "test" profile's database (see
        // ADR-0004: the same reason the test profile already skips Flyway/uses create-drop
        // instead of validate). Postgres-only in practice anyway, since only a real deployment
        // ever runs more than one replica.
        boolean isTestProfile = environment.matchesProfiles("test");
        if (!isTestProfile && !outboxEventRepository.tryAdvisoryXactLock(LOCK_KEY)) {
            // Another replica already holds the lock this tick — its own poll covers the same
            // rows, so there's nothing for this instance to do.
            return;
        }
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent outboxEvent : pending) {
            try {
                DeliveryTaskMessage message = new DeliveryTaskMessage(
                        outboxEvent.getId(), outboxEvent.getEventId(), outboxEvent.getSubscriptionId());
                kafkaTemplate.send(TOPIC, outboxEvent.getEventId().toString(), message).get();
                outboxEvent.setStatus(OutboxStatus.PUBLISHED);
                outboxEvent.setPublishedAt(Instant.now());
                outboxEventRepository.save(outboxEvent);
                meterRegistry.counter("relayhub.outbox.published").increment();
            } catch (Exception e) {
                // Left PENDING — retried on the next poll (at-least-once). See spec.md
                // "Deliberately out of scope" for the consumer-side duplicate-processing gap this implies.
                log.warn("Failed to publish outbox event {}: {}", outboxEvent.getId(), e.getMessage());
                meterRegistry.counter("relayhub.outbox.publish.failed").increment();
            }
        }
    }
}
