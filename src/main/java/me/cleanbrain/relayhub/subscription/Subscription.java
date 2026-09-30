package me.cleanbrain.relayhub.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpoint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** Connects one Source Event to one Target Endpoint's request contract and payload mapping. */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_event_id", nullable = false)
    private SourceEvent sourceEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_endpoint_id", nullable = false)
    private TargetEndpoint targetEndpoint;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    /** JSON template with ${$.jsonpath} placeholders resolved against the Canonical Event's payload. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
    private String targetPayloadTemplate;

    /** Simple boolean-expression text (e.g. {@code status == "DELAYED"}), stored but not evaluated
     *  yet — deliberately deferred, see docs/decisions/ADR-0005-subscription-filter-deferred.md. */
    private String filterExpression;

    // --- Delivery policy: all nullable, null means "use the delivery_settings global default"
    // (see DeliverySettingsService). Not read by DeliveryService yet — Stage 2's job; Stage 1 only
    // adds the columns/API surface so a policy can be set and seen ahead of the engine honoring it.
    private Integer maxAttempts;
    private Integer initialBackoffMs;
    private Integer maxBackoffMs;
    private Double backoffMultiplier;
    private Boolean jitter;
    private Integer timeoutMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;
}
