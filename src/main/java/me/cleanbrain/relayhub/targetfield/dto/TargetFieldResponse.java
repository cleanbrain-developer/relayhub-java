package me.cleanbrain.relayhub.targetfield.dto;

import me.cleanbrain.relayhub.common.FieldDataType;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.targetfield.TargetField;

import java.util.UUID;

public record TargetFieldResponse(
        UUID id,
        String targetKey,
        String key,
        FieldDataType dataType,
        String description,
        String exampleValue,
        boolean required,
        boolean sensitive,
        Status status
) {
    public static TargetFieldResponse from(TargetField field) {
        return new TargetFieldResponse(
                field.getId(),
                field.getTarget().getKey(),
                field.getKey(),
                field.getDataType(),
                field.getDescription(),
                field.getExampleValue(),
                field.isRequired(),
                field.isSensitive(),
                field.getStatus()
        );
    }
}
