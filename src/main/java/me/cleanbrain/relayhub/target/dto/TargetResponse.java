package me.cleanbrain.relayhub.target.dto;

import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.Target;

import java.util.UUID;

public record TargetResponse(UUID id, String key, String name, String description, String baseUrl, Status status) {
    public static TargetResponse from(Target target) {
        return new TargetResponse(target.getId(), target.getKey(), target.getName(), target.getDescription(), target.getBaseUrl(), target.getStatus());
    }
}
