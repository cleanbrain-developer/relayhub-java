package me.cleanbrain.relayhub.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One (Event, Subscription) delivery task, written in the same database transaction as the
 * Canonical Event during ingress — the transactional-outbox guarantee. {@link OutboxPublisher}
 * asynchronously hands each row to Kafka. See specs/003-kafka-outbox/spec.md.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @CreationTimestamp
    private Instant createdAt;

    private Instant publishedAt;
}
