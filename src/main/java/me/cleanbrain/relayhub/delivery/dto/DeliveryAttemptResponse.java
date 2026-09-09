package me.cleanbrain.relayhub.delivery.dto;

import me.cleanbrain.relayhub.delivery.DeliveryAttempt;
import me.cleanbrain.relayhub.delivery.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        UUID eventId,
        UUID subscriptionId,
        UUID targetId,
        int attemptNumber,
        DeliveryStatus status,
        Integer httpStatus,
        String errorMessage,
        Instant attemptedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getEventId(),
                attempt.getSubscriptionId(),
                attempt.getTargetId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getHttpStatus(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }
}
