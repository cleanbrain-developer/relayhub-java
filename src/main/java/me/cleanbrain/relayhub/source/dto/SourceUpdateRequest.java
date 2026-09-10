package me.cleanbrain.relayhub.source.dto;

import jakarta.validation.constraints.NotBlank;

/** Same fields as {@link SourceCreateRequest} minus {@code key}, which is immutable once set. */
public record SourceUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        String authenticationConfig
) {
}
