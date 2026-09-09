package me.cleanbrain.relayhub.delivery.dto;

import me.cleanbrain.relayhub.delivery.Delivery;
import me.cleanbrain.relayhub.delivery.DeliveryState;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID eventId,
        UUID subscriptionId,
        UUID targetId,
        DeliveryState state,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getEventId(),
                delivery.getSubscriptionId(),
                delivery.getTargetId(),
                delivery.getState(),
                delivery.getAttemptCount(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}
