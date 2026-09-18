package me.cleanbrain.relayhub.deliverysettings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Single-row table (id is always {@link #SINGLETON_ID}) holding the delivery retry policy that was
 * previously a hardcoded constant ({@code DeliveryService.MAX_ATTEMPTS}) — invisible to operators
 * and only changeable via a redeploy (maintainer request 2026-09-18). One global policy, not
 * per-Target/per-Subscription, matching Spec 002's "fixed constant policy applies to every
 * Subscription in this spec" — this makes that one constant admin-configurable, nothing more.
 */
@Entity
@Table(name = "delivery_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliverySettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    /** How many attempts (including the first) before a Delivery is marked DEAD. */
    private int maxAttempts;

    /** How often DlqAutoReplayScheduler sweeps the DLQ, in milliseconds. Null means "use the
     *  relayhub.dlq.auto-replay-interval-ms default" — see DeliverySettingsService. */
    private Long autoReplayIntervalMs;

    private Instant updatedAt;
}
