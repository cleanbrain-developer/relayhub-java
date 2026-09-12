package me.cleanbrain.relayhub.sourcefield.dto;

import me.cleanbrain.relayhub.common.FieldDataType;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourcefield.SourceField;

import java.util.UUID;

public record SourceFieldResponse(
        UUID id,
        String sourceKey,
        String sourceEventKey,
        String key,
        String jsonPath,
        FieldDataType dataType,
        String description,
        String exampleValue,
        boolean required,
        boolean sensitive,
        Status status
) {
    public static SourceFieldResponse from(SourceField field) {
        return new SourceFieldResponse(
                field.getId(),
                field.getSourceEvent().getSource().getKey(),
                field.getSourceEvent().getKey(),
                field.getKey(),
                field.getJsonPath(),
                field.getDataType(),
                field.getDescription(),
                field.getExampleValue(),
                field.isRequired(),
                field.isSensitive(),
                field.getStatus()
        );
    }
}
