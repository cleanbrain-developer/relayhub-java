package me.cleanbrain.relayhub.source.dto;

import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.Source;

import java.time.Instant;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        String key,
        String name,
        String description,
        Status status,
        Instant createdAt
) {
    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getKey(),
                source.getName(),
                source.getDescription(),
                source.getStatus(),
                source.getCreatedAt()
        );
    }
}
