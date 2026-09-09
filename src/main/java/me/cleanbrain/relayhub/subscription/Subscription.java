package me.cleanbrain.relayhub.subscription;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.target.Target;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Connects one Source Event to one Target's call convention and payload mapping. */
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
    @JoinColumn(name = "target_id", nullable = false)
    private Target target;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HttpVerb targetMethod;

    @Column(nullable = false)
    private String targetPath;

    /** JSON template with ${$.jsonpath} placeholders resolved against the Canonical Event's payload. */
    @Lob
    @Column(nullable = false)
    private String targetPayloadTemplate;

    /** Retry policy description; enforced starting in the Reliability phase (see ADR/system-design.md). */
    private String retryPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;
}
