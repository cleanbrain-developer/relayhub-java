package me.cleanbrain.relayhub.delivery.dto;

/**
 * True counts (not capped like the list endpoint) — for the dashboard summary. {@code pending}
 * covers every non-terminal state (PENDING/PROCESSING/RETRYING/REPLAYING), not just literal
 * DeliveryState.PENDING — Stage 2's async retry loop passes through PENDING itself almost
 * instantly, so counting only that would under-report what's actually still in flight.
 */
public record DeliverySummaryResponse(long pending, long succeeded, long dead) {
}
