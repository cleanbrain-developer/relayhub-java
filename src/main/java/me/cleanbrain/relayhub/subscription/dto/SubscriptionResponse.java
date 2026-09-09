package me.cleanbrain.relayhub.subscription.dto;

import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.subscription.Subscription;

import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String sourceEventKey,
        String targetKey,
        HttpVerb targetMethod,
        String targetPath,
        Status status
) {
    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getSourceEvent().getKey(),
                subscription.getTarget().getKey(),
                subscription.getTargetMethod(),
                subscription.getTargetPath(),
                subscription.getStatus()
        );
    }
}
