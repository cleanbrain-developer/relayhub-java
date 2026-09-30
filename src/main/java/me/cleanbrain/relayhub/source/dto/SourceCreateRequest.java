package me.cleanbrain.relayhub.source.dto;

import jakarta.validation.constraints.NotBlank;
import me.cleanbrain.relayhub.common.AuthenticationType;

public record SourceCreateRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String description,
        /** Defaults to NONE when omitted — see SourceService.create(). */
        AuthenticationType authenticationType,
        String authenticationConfig
) {
}
