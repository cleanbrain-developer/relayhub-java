package me.cleanbrain.relayhub.delivery.dto;

/** True counts per Delivery state (not capped like the list endpoint) — for the dashboard summary. */
public record DeliverySummaryResponse(long pending, long succeeded, long dead) {
}
