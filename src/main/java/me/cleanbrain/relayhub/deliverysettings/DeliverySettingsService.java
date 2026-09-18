package me.cleanbrain.relayhub.deliverysettings;

import lombok.RequiredArgsConstructor;
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

    private final DeliverySettingsRepository repository;

    public int getMaxAttempts() {
        return repository.findById(DeliverySettings.SINGLETON_ID)
                .map(DeliverySettings::getMaxAttempts)
                .orElse(DEFAULT_MAX_ATTEMPTS);
    }

    @Transactional
    public int updateMaxAttempts(int maxAttempts) {
        if (maxAttempts < MIN_MAX_ATTEMPTS || maxAttempts > MAX_MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    "maxAttempts must be between %d and %d".formatted(MIN_MAX_ATTEMPTS, MAX_MAX_ATTEMPTS));
        }
        DeliverySettings settings = repository.findById(DeliverySettings.SINGLETON_ID)
                .orElseGet(() -> DeliverySettings.builder().id(DeliverySettings.SINGLETON_ID).build());
        settings.setMaxAttempts(maxAttempts);
        settings.setUpdatedAt(Instant.now());
        return repository.save(settings).getMaxAttempts();
    }
}
