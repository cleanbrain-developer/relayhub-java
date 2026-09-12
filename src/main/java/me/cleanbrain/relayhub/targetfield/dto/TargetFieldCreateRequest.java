package me.cleanbrain.relayhub.targetfield.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.FieldDataType;

public record TargetFieldCreateRequest(
        @NotBlank String key,
        @NotNull FieldDataType dataType,
        String description,
        String exampleValue,
        boolean required,
        boolean sensitive
) {
}
