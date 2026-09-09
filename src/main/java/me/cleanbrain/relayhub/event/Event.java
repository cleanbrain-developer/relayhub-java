package me.cleanbrain.relayhub.event;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.sourceevent.Operation;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * RelayHub's internal Canonical Event — never imposed on a Source or Target.
 * See docs/architecture/system-design.md ("Canonical Event").
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID sourceId;

    @Column(nullable = false)
    private UUID sourceEventId;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operation operation;

    private Instant occurredAt;

    @CreationTimestamp
    private Instant receivedAt;

    private String idempotencyKey;

    /** Raw Source payload, preserved verbatim as JSON text. */
    @Lob
    @Column(nullable = false)
    private String payload;
}
