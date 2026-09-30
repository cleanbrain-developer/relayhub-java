package me.cleanbrain.relayhub.targetendpoint.dto;

import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpoint;

import java.util.UUID;

public record TargetEndpointResponse(
        UUID id,
        String targetKey,
        String key,
        String name,
        String description,
        HttpVerb httpMethod,
        String path,
        Integer timeoutOverrideMs,
        String headers,
        Status status
) {
    public static TargetEndpointResponse from(TargetEndpoint endpoint) {
        return new TargetEndpointResponse(
                endpoint.getId(),
                endpoint.getTarget().getKey(),
                endpoint.getKey(),
                endpoint.getName(),
                endpoint.getDescription(),
                endpoint.getHttpMethod(),
                endpoint.getPath(),
                endpoint.getTimeoutOverrideMs(),
                endpoint.getHeaders(),
                endpoint.getStatus()
        );
    }
}
