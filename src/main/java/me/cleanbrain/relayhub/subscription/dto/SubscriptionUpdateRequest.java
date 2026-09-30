package me.cleanbrain.relayhub.subscription.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Same fields as {@link SubscriptionCreateRequest} minus {@code sourceKey}/{@code sourceEventKey}/
 * {@code targetKey}/{@code targetEndpointKey} — the Source Event/Target Endpoint pairing defines
 * the Subscription's identity and isn't editable; create a new Subscription instead if that
 * pairing needs to change.
 */
public record SubscriptionUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String targetPayloadTemplate,
        String filterExpression,
        Integer maxAttempts,
        Integer initialBackoffMs,
        Integer maxBackoffMs,
        Double backoffMultiplier,
        Boolean jitter,
        Integer timeoutMs
) {
}
