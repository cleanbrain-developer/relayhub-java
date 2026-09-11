package me.cleanbrain.relayhub.retention;

import me.cleanbrain.relayhub.delivery.Delivery;
import me.cleanbrain.relayhub.delivery.DeliveryAttempt;
import me.cleanbrain.relayhub.delivery.DeliveryAttemptRepository;
import me.cleanbrain.relayhub.delivery.DeliveryRepository;
import me.cleanbrain.relayhub.delivery.DeliveryState;
import me.cleanbrain.relayhub.delivery.DeliveryStatus;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.outbox.OutboxEvent;
import me.cleanbrain.relayhub.outbox.OutboxEventRepository;
import me.cleanbrain.relayhub.outbox.OutboxStatus;
import me.cleanbrain.relayhub.sourceevent.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies specs the maintainer asked for directly (2026-09-12): a daily job that removes
 * everything from the four high-volume activity tables older than "today", leaving today's rows
 * alone. Same @SpringBootTest/@EmbeddedKafka configuration as the other RANDOM_PORT-adjacent test
 * classes so Spring's test context caching reuses that context — see RelayHubApplicationTests's
 * comment for why a differently-configured context sharing the "test" profile's H2 database name
 * breaks.
 *
 * <p>Every timestamp field this test backdates ({@code receivedAt}/{@code createdAt}/
 * {@code attemptedAt}) is {@code @CreationTimestamp} on its entity, so Hibernate stamps "now" on
 * the initial INSERT regardless of what's set beforehand — each helper below saves once first,
 * then overwrites the timestamp with a second save (an UPDATE, which {@code @CreationTimestamp}
 * does not touch), same pattern already established for {@code Delivery.createdAt} elsewhere in
 * this codebase.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class RetentionCleanupSchedulerTest {

    @Autowired
    RetentionCleanupScheduler scheduler;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    DeliveryRepository deliveryRepository;
    @Autowired
    DeliveryAttemptRepository deliveryAttemptRepository;
    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Test
    @Transactional
    void removesRowsOlderThanTodayButKeepsTodays() {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

        Event oldEvent = backdateEvent(rawEvent(), yesterday);
        Event newEvent = rawEvent();

        Delivery oldDelivery = backdateDelivery(rawDelivery(), yesterday);
        Delivery newDelivery = rawDelivery();

        DeliveryAttempt oldAttempt = backdateAttempt(rawAttempt(), yesterday);
        DeliveryAttempt newAttempt = rawAttempt();

        OutboxEvent oldOutbox = backdateOutbox(rawOutbox(), yesterday);
        OutboxEvent newOutbox = rawOutbox();

        scheduler.cleanUpBeforeToday();

        assertThat(eventRepository.findById(oldEvent.getId())).isEmpty();
        assertThat(eventRepository.findById(newEvent.getId())).isPresent();

        assertThat(deliveryRepository.findById(oldDelivery.getId())).isEmpty();
        assertThat(deliveryRepository.findById(newDelivery.getId())).isPresent();

        assertThat(deliveryAttemptRepository.findById(oldAttempt.getId())).isEmpty();
        assertThat(deliveryAttemptRepository.findById(newAttempt.getId())).isPresent();

        assertThat(outboxEventRepository.findById(oldOutbox.getId())).isEmpty();
        assertThat(outboxEventRepository.findById(newOutbox.getId())).isPresent();
    }

    private Event rawEvent() {
        return eventRepository.saveAndFlush(Event.builder()
                .sourceId(UUID.randomUUID())
                .sourceEventId(UUID.randomUUID())
                .resourceType("thing")
                .resourceId("r1")
                .operation(Operation.CREATED)
                .payload("{}")
                .build());
    }

    private Event backdateEvent(Event event, Instant receivedAt) {
        event.setReceivedAt(receivedAt);
        return eventRepository.saveAndFlush(event);
    }

    private Delivery rawDelivery() {
        return deliveryRepository.saveAndFlush(Delivery.builder()
                .eventId(UUID.randomUUID())
                .subscriptionId(UUID.randomUUID())
                .targetId(UUID.randomUUID())
                .state(DeliveryState.SUCCEEDED)
                .attemptCount(1)
                .build());
    }

    private Delivery backdateDelivery(Delivery delivery, Instant createdAt) {
        delivery.setCreatedAt(createdAt);
        return deliveryRepository.saveAndFlush(delivery);
    }

    private DeliveryAttempt rawAttempt() {
        return deliveryAttemptRepository.saveAndFlush(DeliveryAttempt.builder()
                .deliveryId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .subscriptionId(UUID.randomUUID())
                .targetId(UUID.randomUUID())
                .attemptNumber(1)
                .status(DeliveryStatus.SUCCESS)
                .build());
    }

    private DeliveryAttempt backdateAttempt(DeliveryAttempt attempt, Instant attemptedAt) {
        attempt.setAttemptedAt(attemptedAt);
        return deliveryAttemptRepository.saveAndFlush(attempt);
    }

    private OutboxEvent rawOutbox() {
        return outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .eventId(UUID.randomUUID())
                .subscriptionId(UUID.randomUUID())
                .status(OutboxStatus.PUBLISHED)
                .build());
    }

    private OutboxEvent backdateOutbox(OutboxEvent outbox, Instant createdAt) {
        outbox.setCreatedAt(createdAt);
        return outboxEventRepository.saveAndFlush(outbox);
    }
}
