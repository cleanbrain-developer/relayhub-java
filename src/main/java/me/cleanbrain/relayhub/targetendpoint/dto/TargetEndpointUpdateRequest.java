package me.cleanbrain.relayhub.targetendpoint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.HttpVerb;

/** Same fields as {@link TargetEndpointCreateRequest} minus {@code key}, which is immutable once set. */
public record TargetEndpointUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull HttpVerb httpMethod,
        @NotBlank String path,
        Integer timeoutOverrideMs,
        String headers
) {
}
