package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Picks up {@code RETRYING} Deliveries once their {@code nextAttemptAt} has elapsed and re-attempts
 * them via {@link DeliveryService#processDueRetry} — the non-blocking replacement for the old
 * in-process {@code Thread.sleep} backoff loop (Stage 2 of the integration-platform overhaul,
 * maintainer request 2026-09-30; see DeliveryService's own Javadoc and
 * db/migration/V12__delivery_retry_state.sql).
 *
 * <p>Deliberately a separate scheduler from {@link DlqAutoReplayScheduler}, not a merged one,
 * despite both being "pick due deliveries off a queue and re-attempt them" — they operate on
 * different time scales and are driven by different things. This one ticks on a short, fixed
 * cadence and is driven entirely by each Delivery's own computed {@code nextAttemptAt} (typically
 * sub-second to low tens of seconds, per DeliveryPolicy); DlqAutoReplayScheduler ticks against one
 * admin-configurable interval shared by the whole DEAD backlog (30s-1h). Merging them would force
 * one polling cadence to serve both scales badly.
 *
 * <p>Same bounded-batch (see DeliveryRepository.findTop20By...), same-profile advisory-lock guard,
 * and same per-item-independent-transaction reasoning as DlqAutoReplayScheduler — see that class's
 * Javadoc for the full explanation (single replica today per cleanbrain-me-infra, so this is latent
 * safety, not yet an observed bug).
 */
@Component
@RequiredArgsConstructor
public class DeliveryRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryScheduler.class);

    // Distinct from DlqAutoReplayScheduler's LOCK_KEY (4_921_733_001L) — two independent sweeps,
    // each with its own lock so one never blocks on the other.
    private static final long LOCK_KEY = 4_921_733_002L;

    // Fixed, not admin-configurable like DlqAutoReplayScheduler's interval: this trades up to
    // TICK_MS of added latency/jitter on top of each Delivery's own computed backoff for a modest,
    // predictable DB polling load — reasonable given this project's resource constraints (see
    // cleanbrain-me-infra CLAUDE.md: 2 vCPU/4GB). Revisit only if a real workload needs tighter
    // backoff granularity than this affords.
    private static final long TICK_MS = 1000;

    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;
    private final DataSource dataSource;
    private final Environment environment;

    @Scheduled(fixedDelay = TICK_MS)
    public void tick() {
        // pg_try_advisory_lock doesn't exist on H2 — the "test" profile's database (see
        // ADR-0004). Postgres-only in practice anyway, since only a real deployment ever runs more
        // than one replica.
        if (environment.matchesProfiles("test")) {
            runDueBatch();
            return;
        }
        try (Connection lockConnection = dataSource.getConnection()) {
            if (!tryAcquireLock(lockConnection)) {
                log.debug("Another instance already holds the delivery-retry lock; skipping this tick.");
                return;
            }
            try {
                runDueBatch();
            } finally {
                releaseLock(lockConnection);
            }
        } catch (SQLException e) {
            log.warn("Delivery-retry tick skipped — could not acquire a connection for the advisory lock: {}", e.getMessage());
        }
    }

    /** The actual batch — also called directly by tests instead of waiting on the scheduler's tick. */
    public void runDueBatch() {
        List<Delivery> due = deliveryRepository
                .findTop20ByStateAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(DeliveryState.RETRYING, Instant.now());
        for (Delivery delivery : due) {
            try {
                deliveryService.processDueRetry(delivery.getId());
            } catch (Exception e) {
                // One delivery's retry failing unexpectedly must not stop the rest of this batch.
                log.warn("Scheduled retry failed for delivery {}: {}", delivery.getId(), e.getMessage());
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
            log.warn("Failed to release the delivery-retry advisory lock (it will still auto-release when this connection closes): {}", e.getMessage());
        }
    }
}
