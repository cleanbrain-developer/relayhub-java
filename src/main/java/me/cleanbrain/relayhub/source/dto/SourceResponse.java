package me.cleanbrain.relayhub.source.dto;

import me.cleanbrain.relayhub.common.AuthenticationType;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.Source;

import java.time.Instant;
import java.util.UUID;

/** authenticationType is echoed back (not a secret); authenticationConfig stays masked/omitted,
 *  same as before this field existed. */
public record SourceResponse(
        UUID id,
        String key,
        String name,
        String description,
        AuthenticationType authenticationType,
        Status status,
        Instant createdAt
) {
    public static SourceResponse from(Source source) {
        return new SourceResponse(
                source.getId(),
                source.getKey(),
                source.getName(),
                source.getDescription(),
                source.getAuthenticationType(),
                source.getStatus(),
                source.getCreatedAt()
        );
    }
}
