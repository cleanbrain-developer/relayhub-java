package me.cleanbrain.relayhub.source.dto;

import jakarta.validation.constraints.NotBlank;

public record SourceCreateRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String description,
        String authenticationConfig
) {
}
