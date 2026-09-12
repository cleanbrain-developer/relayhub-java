package me.cleanbrain.relayhub.delivery;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One delivery attempt of a Canonical Event to a Target via a Subscription, belonging to a
 * parent {@link Delivery}. See specs/002-retry-dlq-replay/spec.md.
 */
@Entity
@Table(name = "delivery_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID deliveryId;

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private UUID targetId;

    @Column(nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    /** What was actually sent to the Target — captured so an operator can see the real request,
     *  not only its outcome (2026-09-13, admin console follow-up). */
    private String requestMethod;

    private String requestUrl;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String requestBody;

    private Integer httpStatus;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String responseBody;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    private String errorMessage;

    @CreationTimestamp
    private Instant attemptedAt;
}
