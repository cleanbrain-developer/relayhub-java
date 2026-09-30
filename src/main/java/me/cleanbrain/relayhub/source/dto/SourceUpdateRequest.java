package me.cleanbrain.relayhub.source.dto;

import jakarta.validation.constraints.NotBlank;
import me.cleanbrain.relayhub.common.AuthenticationType;

/** Same fields as {@link SourceCreateRequest} minus {@code key}, which is immutable once set. */
public record SourceUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        AuthenticationType authenticationType,
        String authenticationConfig
) {
}
