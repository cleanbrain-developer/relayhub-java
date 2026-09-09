package me.cleanbrain.relayhub.target.dto;

import jakarta.validation.constraints.NotBlank;

public record TargetCreateRequest(
        @NotBlank String key,
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String baseUrl,
        String authenticationConfig
) {
}
