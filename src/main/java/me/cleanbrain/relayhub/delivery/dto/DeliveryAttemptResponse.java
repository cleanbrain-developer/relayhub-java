package me.cleanbrain.relayhub.delivery.dto;

import me.cleanbrain.relayhub.delivery.DeliveryAttempt;
import me.cleanbrain.relayhub.delivery.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID id,
        UUID deliveryId,
        UUID eventId,
        UUID subscriptionId,
        UUID targetId,
        int attemptNumber,
        DeliveryStatus status,
        String requestMethod,
        String requestUrl,
        String requestBody,
        Integer httpStatus,
        String responseBody,
        String errorMessage,
        Instant attemptedAt
) {
    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
                attempt.getId(),
                attempt.getDeliveryId(),
                attempt.getEventId(),
                attempt.getSubscriptionId(),
                attempt.getTargetId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getRequestMethod(),
                attempt.getRequestUrl(),
                attempt.getRequestBody(),
                attempt.getHttpStatus(),
                attempt.getResponseBody(),
                attempt.getErrorMessage(),
                attempt.getAttemptedAt()
        );
    }
}
