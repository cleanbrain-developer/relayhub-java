package me.cleanbrain.relayhub.targetendpoint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.HttpVerb;

public record TargetEndpointCreateRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String description,
        @NotNull HttpVerb httpMethod,
        @NotBlank String path,
        Integer timeoutOverrideMs,
        String headers
) {
}
