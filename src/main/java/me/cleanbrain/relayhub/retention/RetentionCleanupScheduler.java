package me.cleanbrain.relayhub.retention;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.delivery.DeliveryAttemptRepository;
import me.cleanbrain.relayhub.delivery.DeliveryRepository;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.outbox.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Daily capacity-management cleanup — maintainer request (2026-09-12): {@code relayhub-demo-systems}
 * generates continuous traffic 24/7 in production (see docs/status/current-state.md), so
 * {@code events}/{@code deliveries}/{@code delivery_attempts}/{@code outbox_events} grow without
 * bound. Deletes every row from those four tables older than the start of today (UTC) — none of
 * them have a DB-level foreign key to any of the others (see db/migration/V1__init_schema.sql),
 * so deletion order between them doesn't matter.
 *
 * <p>This is deliberately aggressive (same-day-only retention) for a demo/personal-scale service
 * whose whole point is showing current activity, not preserving delivery history — Source/Target/
 * Subscription registrations themselves are untouched, only the high-volume activity tables.
 * {@code Delivery}'s own {@code uk_deliveries_event_subscription} unique constraint means a
 * cleaned-up Event's Subscriptions could, in principle, be re-delivered to if the same
 * idempotency key were ever replayed after cleanup — not a real risk at this traffic pattern, but
 * worth knowing if retention is ever revisited.
 */
@Component
@RequiredArgsConstructor
public class RetentionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void cleanUpBeforeToday() {
        Instant cutoff = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        int attempts = deliveryAttemptRepository.deleteByAttemptedAtBefore(cutoff);
        int deliveries = deliveryRepository.deleteByCreatedAtBefore(cutoff);
        int outbox = outboxEventRepository.deleteByCreatedAtBefore(cutoff);
        int events = eventRepository.deleteByReceivedAtBefore(cutoff);

        log.info("Retention cleanup (cutoff={}): removed {} delivery attempts, {} deliveries, {} outbox events, {} events",
                cutoff, attempts, deliveries, outbox, events);
    }
}
