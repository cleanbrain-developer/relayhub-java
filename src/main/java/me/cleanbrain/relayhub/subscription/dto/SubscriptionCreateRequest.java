package me.cleanbrain.relayhub.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.HttpVerb;

public record SubscriptionCreateRequest(
        @NotBlank String sourceKey,
        @NotBlank String sourceEventKey,
        @NotBlank String targetKey,
        @NotBlank String name,
        @NotBlank String description,
        @NotNull HttpVerb targetMethod,
        @NotBlank String targetPath,
        @NotBlank String targetPayloadTemplate,
        String retryPolicy
) {
}
