package me.cleanbrain.relayhub.target.dto;

import jakarta.validation.constraints.NotBlank;
import me.cleanbrain.relayhub.common.AuthenticationType;

public record TargetCreateRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String baseUrl,
        /** Defaults to NONE when omitted — see TargetService.create(). */
        AuthenticationType authenticationType,
        String authenticationConfig
) {
}
