package me.cleanbrain.relayhub.sourcefield;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.cleanbrain.relayhub.common.FieldDataType;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One field a SourceEvent's payload can supply, registered so MappingBuilder can offer it as a
 * dropdown choice instead of requiring free-typed JSONPath text. Scoped to the SourceEvent, not
 * the bare Source — two events on the same Source can have different payload shapes, and
 * {@code jsonPath} is only meaningful relative to one event's payload. See
 * specs/006-field-registry/spec.md.
 */
@Entity
@Table(name = "source_fields", uniqueConstraints = @UniqueConstraint(columnNames = {"source_event_id", "field_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_event_id", nullable = false)
    private SourceEvent sourceEvent;

    /** Short field name shown in the mapping UI, e.g. "customerNo". Column named field_key, not
     *  key — "key" is a reserved word in some SQL dialects and every sibling table here avoids it. */
    @Column(name = "field_key", nullable = false)
    private String key;

    /** Extraction path against the raw Source payload, e.g. "$.customerNo". */
    @Column(nullable = false)
    private String jsonPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldDataType dataType;

    private String description;

    private String exampleValue;

    @Column(nullable = false)
    private boolean required;

    /** PII/secret marker — no masking/redaction behavior implemented yet, just captured so it
     *  doesn't need a migration later if that's ever built. See spec.md. */
    @Column(nullable = false)
    private boolean sensitive;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;
}
