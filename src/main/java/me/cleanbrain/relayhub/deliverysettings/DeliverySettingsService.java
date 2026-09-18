package me.cleanbrain.relayhub.deliverysettings;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeliverySettingsService {

    /** Matches V4__delivery_settings.sql's seeded default — defensive fallback only; the real
     *  source of truth is the singleton row, which Flyway guarantees exists against Postgres. The
     *  test profile runs with Flyway disabled (H2, ddl-auto: create-drop — see ADR-0004), so this
     *  fallback is what tests actually exercise. */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MIN_MAX_ATTEMPTS = 1;
    private static final int MAX_MAX_ATTEMPTS = 10;

    private static final long MIN_AUTO_REPLAY_INTERVAL_MS = 5_000;
    private static final long MAX_AUTO_REPLAY_INTERVAL_MS = 3_600_000;

    private final DeliverySettingsRepository repository;

    /** Same property DlqAutoReplayScheduler used before this became DB-configurable — still the
     *  fallback for a fresh row (auto_replay_interval_ms is NULL until an operator sets it), and
     *  still what the "test" profile's application-test.yml override (1 hour) governs, since
     *  Flyway never seeds a row against H2. */
    @Value("${relayhub.dlq.auto-replay-interval-ms:30000}")
    private long defaultAutoReplayIntervalMs;

    public int getMaxAttempts() {
        return findSettings().map(DeliverySettings::getMaxAttempts).orElse(DEFAULT_MAX_ATTEMPTS);
    }

    public long getAutoReplayIntervalMs() {
        return findSettings().map(DeliverySettings::getAutoReplayIntervalMs)
                .orElse(defaultAutoReplayIntervalMs);
    }

    @Transactional
    public DeliverySettings updateSettings(int maxAttempts, long autoReplayIntervalMs) {
        if (maxAttempts < MIN_MAX_ATTEMPTS || maxAttempts > MAX_MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "maxAttempts must be between %d and %d".formatted(MIN_MAX_ATTEMPTS, MAX_MAX_ATTEMPTS));
        }
        if (autoReplayIntervalMs < MIN_AUTO_REPLAY_INTERVAL_MS || autoReplayIntervalMs > MAX_AUTO_REPLAY_INTERVAL_MS) {
            throw new IllegalArgumentException("autoReplayIntervalMs must be between %d and %d"
                    .formatted(MIN_AUTO_REPLAY_INTERVAL_MS, MAX_AUTO_REPLAY_INTERVAL_MS));
        }
        DeliverySettings settings = repository.findById(DeliverySettings.SINGLETON_ID)
                .orElseGet(() -> DeliverySettings.builder().id(DeliverySettings.SINGLETON_ID).build());
        settings.setMaxAttempts(maxAttempts);
        settings.setAutoReplayIntervalMs(autoReplayIntervalMs);
        settings.setUpdatedAt(Instant.now());
        return repository.save(settings);
    }

    private java.util.Optional<DeliverySettings> findSettings() {
        return repository.findById(DeliverySettings.SINGLETON_ID);
    }
}
