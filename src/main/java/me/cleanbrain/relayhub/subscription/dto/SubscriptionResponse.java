package me.cleanbrain.relayhub.subscription.dto;

import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.subscription.Subscription;

import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String sourceEventKey,
        String targetKey,
        String name,
        String description,
        HttpVerb targetMethod,
        String targetPath,
        String targetPayloadTemplate,
        String retryPolicy,
        Status status
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getSourceEvent().getKey(),
                subscription.getTarget().getKey(),
                subscription.getName(),
                subscription.getDescription(),
                subscription.getTargetMethod(),
                subscription.getTargetPath(),
                subscription.getTargetPayloadTemplate(),
                subscription.getRetryPolicy(),
                subscription.getStatus()
        );
    }
}
