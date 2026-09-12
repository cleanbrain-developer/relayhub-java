package me.cleanbrain.relayhub.targetfield;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.FieldDataType;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.Target;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One field a Target's payload can accept, registered so MappingBuilder can offer it as a
 * dropdown choice instead of requiring a free-typed field name. Scoped to the Target itself (not
 * per-endpoint/path) for v1 — see specs/006-field-registry/spec.md.
 */
@Entity
@Table(name = "target_fields", uniqueConstraints = @UniqueConstraint(columnNames = {"target_id", "field_key"}))
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
    @JoinColumn(name = "target_id", nullable = false)
    private Target target;

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
