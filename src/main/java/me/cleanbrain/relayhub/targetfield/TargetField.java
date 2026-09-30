package me.cleanbrain.relayhub.targetfield;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.FieldDataType;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpoint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One field a Target Endpoint's request payload can accept, registered so MappingBuilder can offer
 * it as a dropdown choice instead of requiring a free-typed field name. Scoped to the
 * TargetEndpoint (not the bare Target) — the same Target system can expose multiple endpoints with
 * different request shapes, so a field is only meaningful relative to one endpoint's contract. See
 * specs/006-field-registry/spec.md for the original (Target-scoped) design this replaces.
 */
@Entity
@Table(name = "target_fields", uniqueConstraints = @UniqueConstraint(columnNames = {"target_endpoint_id", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_endpoint_id", nullable = false)
    private TargetEndpoint targetEndpoint;

    /** Short field name shown in the mapping UI, e.g. "dealerId". Column named field_key — see
     *  SourceField's matching comment. */
    @Column(name = "field_key", nullable = false)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldDataType dataType;

    private String description;

    private String exampleValue;

    @Column(nullable = false)
    private boolean required;

    /** PII/secret marker — no masking/redaction behavior implemented yet. See spec.md. */
    @Column(nullable = false)
    private boolean sensitive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;
}
