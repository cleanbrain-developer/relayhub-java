package me.cleanbrain.relayhub.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.HttpVerb;

/**
 * Same fields as {@link SubscriptionCreateRequest} minus {@code sourceKey}/{@code sourceEventKey}/
 * {@code targetKey} — the Source Event/Target pairing defines the Subscription's identity and
 * isn't editable; create a new Subscription instead if that pairing needs to change.
 */
public record SubscriptionUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull HttpVerb targetMethod,
        @NotBlank String targetPath,
        @NotBlank String targetPayloadTemplate,
        String retryPolicy
) {
}
