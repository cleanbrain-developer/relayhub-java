package me.cleanbrain.relayhub.common;

/** Declared shape of a registered SourceField/TargetField value — for UI hints and light
 *  validation, not a full JSON Schema replacement. See specs/006-field-registry/spec.md. */
public enum FieldDataType {
    STRING,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY,
    DATE
}
