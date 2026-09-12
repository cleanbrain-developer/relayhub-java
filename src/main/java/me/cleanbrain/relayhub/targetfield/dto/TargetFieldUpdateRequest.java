package me.cleanbrain.relayhub.targetfield.dto;

import jakarta.validation.constraints.NotNull;
import me.cleanbrain.relayhub.common.FieldDataType;

public record TargetFieldUpdateRequest(
        @NotNull FieldDataType dataType,
        String description,
        String exampleValue,
        boolean required,
        boolean sensitive
) {
}
