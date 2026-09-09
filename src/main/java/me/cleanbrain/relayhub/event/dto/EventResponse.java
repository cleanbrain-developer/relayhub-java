package me.cleanbrain.relayhub.event.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.sourceevent.Operation;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID sourceId,
        UUID sourceEventId,
        String resourceType,
        String resourceId,
        Operation operation,
        Instant occurredAt,
        Instant receivedAt,
        String idempotencyKey,
        JsonNode payload
) {
    public static EventResponse from(Event event, ObjectMapper objectMapper) {
        JsonNode payloadNode;
        try {
            payloadNode = objectMapper.readTree(event.getPayload());
        } catch (Exception e) {
            payloadNode = objectMapper.getNodeFactory().textNode(event.getPayload());
        }
        return new EventResponse(
                event.getId(),
                event.getSourceId(),
                event.getSourceEventId(),
                event.getResourceType(),
                event.getResourceId(),
                event.getOperation(),
                event.getOccurredAt(),
                event.getReceivedAt(),
                event.getIdempotencyKey(),
                payloadNode
        );
    }
}
