package me.cleanbrain.relayhub.delivery;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One (Event, Subscription) delivery unit. Owns the DeliveryAttempt history for that pairing and
 * the overall recoverable state (DLQ = DEAD). See specs/002-retry-dlq-replay/spec.md.
 *
 * <p>The (eventId, subscriptionId) uniqueness is also enforced at the DB level so that a delivery
 * task redelivered by Kafka (e.g. after a worker crash before offset commit) cannot create a
 * second row for the same pairing — see DeliveryService#deliver and ADR-0004.
 */
@Entity
@Table(name = "deliveries", uniqueConstraints = @UniqueConstraint(name = "uk_deliveries_event_subscription", columnNames = {"event_id", "subscription_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryState state;

    @Column(nullable = false)
    private int attemptCount;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
