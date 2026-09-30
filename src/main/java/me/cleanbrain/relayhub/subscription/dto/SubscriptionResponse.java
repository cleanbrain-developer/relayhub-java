package me.cleanbrain.relayhub.subscription.dto;

import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.subscription.Subscription;

import java.util.List;
import java.util.UUID;

/**
 * {@code effectiveMaxAttempts} is {@code maxAttempts} resolved against the
 * {@code delivery_settings} global default (DeliverySettingsService) when the Subscription hasn't
 * overridden it — same "always show a concrete value" pattern DeliverySettingsService itself uses.
 * The rest of the delivery-policy fields (initialBackoffMs, maxBackoffMs, backoffMultiplier,
 * jitter, timeoutMs) are returned as stored (possibly null = "not configured"), since there is no
 * real global default for them yet to resolve against — DeliveryService doesn't read any of them
 * until Stage 2 wires a configurable retry engine.
 */
public record SubscriptionResponse(
        UUID id,
        String sourceKey,
        String sourceEventKey,
        String targetKey,
        String targetEndpointKey,
        String name,
        String description,
        HttpVerb targetMethod,
        String targetPath,
        String targetPayloadTemplate,
        String filterExpression,
        Integer maxAttempts,
        int effectiveMaxAttempts,
        Integer initialBackoffMs,
        Integer maxBackoffMs,
        Double backoffMultiplier,
        Boolean jitter,
        Integer timeoutMs,
        Status status,
        /** Field-registry warnings (maintainer request 2026-09-30) — informational only, never
         *  blocks create/update. See MappingValidationService. Empty, not null, when there's
         *  nothing to warn about. */
        List<String> mappingWarnings
) {
    public static SubscriptionResponse from(Subscription subscription, int effectiveMaxAttempts, List<String> mappingWarnings) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getSourceEvent().getSource().getKey(),
                subscription.getSourceEvent().getKey(),
                subscription.getTargetEndpoint().getTarget().getKey(),
                subscription.getTargetEndpoint().getKey(),
                subscription.getName(),
                subscription.getDescription(),
                subscription.getTargetEndpoint().getHttpMethod(),
                subscription.getTargetEndpoint().getPath(),
                subscription.getTargetPayloadTemplate(),
                subscription.getFilterExpression(),
                subscription.getMaxAttempts(),
                effectiveMaxAttempts,
                subscription.getInitialBackoffMs(),
                subscription.getMaxBackoffMs(),
                subscription.getBackoffMultiplier(),
                subscription.getJitter(),
                subscription.getTimeoutMs(),
                subscription.getStatus(),
                mappingWarnings
        );
    }
}
