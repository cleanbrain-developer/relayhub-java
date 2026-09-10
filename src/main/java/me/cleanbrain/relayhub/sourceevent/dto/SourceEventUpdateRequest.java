package me.cleanbrain.relayhub.sourceevent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.sourceevent.Operation;

/** Same fields as {@link SourceEventCreateRequest} minus {@code key}, which is immutable once set. */
public record SourceEventUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String resourceType,
        @NotNull Operation operation,
        @NotBlank String resourceIdPath,
        String occurredAtPath,
        String idempotencyKeyPath,
        String idempotencyHeader,
        String payloadSchema
) {
}
