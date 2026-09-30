package me.cleanbrain.relayhub.target.dto;

import jakarta.validation.constraints.NotBlank;
import me.cleanbrain.relayhub.common.AuthenticationType;

/** Same fields as {@link TargetCreateRequest} minus {@code key}, which is immutable once set. */
public record TargetUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String baseUrl,
        AuthenticationType authenticationType,
        String authenticationConfig
) {
}
