package me.cleanbrain.relayhub.delivery.dto;

import java.time.Instant;

public record DlqScheduleResponse(long intervalMs, Instant lastRunAt, Instant nextRunAt) {
}
