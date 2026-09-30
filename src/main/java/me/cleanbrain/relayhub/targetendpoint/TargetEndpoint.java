package me.cleanbrain.relayhub.targetendpoint;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.Target;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Registration of one callable API endpoint on a Target system, e.g. "create-customer" (POST
 * /customers) on Target "salesforce". A Target can expose several of these with different request
 * shapes — this is what Subscription actually points at (not the bare Target), mirroring
 * SourceEvent's role on the Source side.
 */
@Entity
@Table(name = "target_endpoints", uniqueConstraints = @UniqueConstraint(columnNames = {"target_id", "key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private Target target;

    @Column(nullable = false)
    private String key;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private HttpVerb httpMethod;

    @Column(nullable = false)
    private String path;

    /** Optional per-endpoint override of the delivery timeout, in milliseconds. Null means "use
     *  the delivery-wide default". Not read by DeliveryService yet (Stage 2). */
    private Integer timeoutOverrideMs;

    /** Optional extra headers to send on every call to this endpoint, as raw text (e.g. one
     *  "Name: value" per line). Not read by DeliveryService yet (Stage 2). */
    @Column(length = 4000)
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;
}
