package me.cleanbrain.relayhub.sourceevent.dto;

import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.Operation;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;

import java.util.UUID;

public record SourceEventResponse(
        UUID id,
        String sourceKey,
        String key,
        String name,
        String description,
        String resourceType,
        Operation operation,
        HttpVerb ingressMethod,
        String ingressPath,
        String resourceIdPath,
        String occurredAtPath,
        String idempotencyKeyPath,
        String idempotencyHeader,
        String payloadSchema,
        Status status
) {
    public static SourceEventResponse from(SourceEvent sourceEvent) {
        return new SourceEventResponse(
                sourceEvent.getId(),
                sourceEvent.getSource().getKey(),
                sourceEvent.getKey(),
                sourceEvent.getName(),
                sourceEvent.getDescription(),
                sourceEvent.getResourceType(),
                sourceEvent.getOperation(),
                sourceEvent.getIngressMethod(),
                sourceEvent.getIngressPath(),
                sourceEvent.getResourceIdPath(),
                sourceEvent.getOccurredAtPath(),
                sourceEvent.getIdempotencyKeyPath(),
                sourceEvent.getIdempotencyHeader(),
                sourceEvent.getPayloadSchema(),
                sourceEvent.getStatus()
        );
    }
}
