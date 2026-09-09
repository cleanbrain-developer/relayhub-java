package me.cleanbrain.relayhub.sourceevent;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.Source;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Registration of one Source-emitted event type, e.g. "customer-created" on Source "sap".
 * Owns the auto-generated Ingress URL and the JSONPath extraction rules for that event.
 */
@Entity
@Table(name = "source_events", uniqueConstraints = @UniqueConstraint(columnNames = "ingress_path"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String resourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingress_method", nullable = false)
    private HttpVerb ingressMethod;

    @Column(name = "ingress_path", nullable = false)
    private String ingressPath;

    /** JSONPath into the Source payload identifying the changed resource, e.g. $.customerNo. Required. */
    @Column(nullable = false)
    private String resourceIdPath;

    /** Optional JSONPath for the Source's own event timestamp. */
    private String occurredAtPath;

    /** Optional JSONPath for an idempotency key inside the payload. */
    private String idempotencyKeyPath;

    /** Optional HTTP header name carrying an idempotency key instead. */
    private String idempotencyHeader;

    /** Optional JSON Schema (as text) the Source payload must satisfy. */
    @Lob
    private String payloadSchema;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;
}
