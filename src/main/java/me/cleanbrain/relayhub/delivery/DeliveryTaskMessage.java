package me.cleanbrain.relayhub.delivery;

import java.util.UUID;

/** Kafka payload for one queued (Event, Subscription) delivery task. See OutboxPublisher/DeliveryWorker. */
public record DeliveryTaskMessage(UUID outboxEventId, UUID eventId, UUID subscriptionId) {
}
