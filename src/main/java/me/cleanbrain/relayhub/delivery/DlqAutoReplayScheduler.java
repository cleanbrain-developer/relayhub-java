package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Periodically re-attempts DEAD deliveries automatically, instead of leaving DLQ replay as a
 * purely operator-triggered action (see DeliveryController's manual Replay button / spec.md
 * "Deliberately out of scope" — this is the follow-up that closes that gap). Requested so the
 * admin console's Live page can show DLQ items actually leaving the queue over time, not just
 * accumulating in it.
 *
 * <p>Bounded to 10 oldest DEAD deliveries per tick (see DeliveryRepository.findTop10ByStateOrderByUpdatedAtAsc)
 * and a 30s default interval (relayhub.dlq.auto-replay-interval-ms) so a large or persistently
 * failing backlog can't turn this into a tight retry storm against an already-struggling Target.
 * Each delivery goes through the exact same DeliveryService.replay used by the manual Replay
 * button, so it gets the same one-attempt-per-call semantics and the same "delivery" live-activity
 * broadcast.
 *
 * <p>{@code lastRunAt} is updated at the very top of every tick — regardless of whether the DLQ
 * had anything to replay — so DlqScheduleController's countdown stays accurate even when the
 * queue is empty. Exposed (not just internal) because the maintainer asked for the console to
 * show a literal countdown to the next sweep, not just its eventual effects.
 *
 * <p>Guarded by a Postgres session-level advisory lock (self-review finding, 2026-09-17): the
 * Deployment runs a single replica today (see cleanbrain-me-infra), so this was latent, not yet
 * observed — but without it, scaling to more than one replica would have every instance pull the
 * same oldest-DEAD batch and call {@code deliveryService.replay} on the same delivery ids
 * concurrently, i.e. real duplicate outbound calls to a Target, not just wasted work. A plain
 * {@code @Transactional} + {@code pg_try_advisory_xact_lock} was considered and rejected: each
 * {@code replay()} call is deliberately its own independent transaction (a mid-batch failure must
 * not roll back earlier successful replays in the same tick), and wrapping the whole method in one
 * outer transaction would merge them. Session-level {@code pg_try_advisory_lock}/{@code
 * pg_advisory_unlock} on a dedicated, manually-held connection (not one borrowed from the
 * `@Transactional`-managed pool) keeps the lock's lifetime independent of any individual replay's
 * transaction.
 */
@Component
@RequiredArgsConstructor
public class DlqAutoReplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqAutoReplayScheduler.class);

    // Arbitrary but fixed — pg_advisory_lock keys are just int8s shared by convention among
    // whoever uses them; this one is only ever used here, so any fixed value works.
    private static final long LOCK_KEY = 4_921_733_001L;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;
    private final DataSource dataSource;
    private final Environment environment;

    @Value("${relayhub.dlq.auto-replay-interval-ms:30000}")
    private long intervalMs;

    private volatile Instant lastRunAt = Instant.now();

    @Scheduled(fixedDelayString = "${relayhub.dlq.auto-replay-interval-ms:30000}")
    public void replayDeadDeliveries() {
        // pg_try_advisory_lock doesn't exist on H2 — the "test" profile's database (see
        // ADR-0004: the same reason the test profile already skips Flyway/uses create-drop
        // instead of validate). Postgres-only in practice anyway, since only a real deployment
        // ever runs more than one replica; a test run has nothing to race against regardless.
        if (environment.matchesProfiles("test")) {
            runReplayBatch();
            return;
        }
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryAcquireLock(lockConnection)) {
                log.debug("Another instance already holds the DLQ auto-replay lock; skipping this tick.");
                return;
            }
            try {
                runReplayBatch();
            } finally {
                releaseLock(lockConnection);
            }
        } catch (SQLException e) {
            log.warn("DLQ auto-replay tick skipped — could not acquire a connection for the advisory lock: {}", e.getMessage());
        }
    }

    private void runReplayBatch() {
        lastRunAt = Instant.now();
        List<Delivery> deadDeliveries = deliveryRepository.findTop10ByStateOrderByUpdatedAtAsc(DeliveryState.DEAD);
        for (Delivery delivery : deadDeliveries) {
            try {
                deliveryService.replay(delivery.getId());
            } catch (Exception e) {
                // One delivery's replay failing (e.g. it was manually replayed a moment ago and is
                // no longer DEAD) must not stop the rest of this batch from being attempted.
                log.warn("Auto-replay failed for delivery {}: {}", delivery.getId(), e.getMessage());
            }
        }
    }

    private boolean tryAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, LOCK_KEY);
            statement.execute();
        } catch (SQLException e) {
            log.warn("Failed to release the DLQ auto-replay advisory lock (it will still auto-release when this connection closes): {}", e.getMessage());
        }
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }
}
