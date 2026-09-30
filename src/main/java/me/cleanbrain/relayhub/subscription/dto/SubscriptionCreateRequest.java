package me.cleanbrain.relayhub.subscription.dto;

import jakarta.validation.constraints.NotBlank;

/** Delivery-policy fields are all optional — omitted means "use the delivery_settings global
 *  default" (maxAttempts) or "not yet wired" (the rest — see Subscription.java, Stage 2's job).
 *  filterExpression is optional free text, stored but not evaluated (see ADR-0010). */
public record SubscriptionCreateRequest(
        @NotBlank String sourceKey,
        @NotBlank String sourceEventKey,
        @NotBlank String targetKey,
        @NotBlank String targetEndpointKey,
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
