package me.cleanbrain.relayhub.sourcefield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.FieldDataType;

public record SourceFieldCreateRequest(
        @NotBlank String key,
        @NotBlank String jsonPath,
        @NotNull FieldDataType dataType,
        String description,
        String exampleValue,
        boolean required,
        boolean sensitive
) {
}
