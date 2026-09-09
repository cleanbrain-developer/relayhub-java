package me.cleanbrain.relayhub.ingress.dto;

import java.util.UUID;

public record IngressResponse(UUID eventId, int deliveryCount) {
}
