package me.cleanbrain.relayhub.delivery.dto;

/**
 * Summary of a {@code POST /api/deliveries/bulk-replay} batch — not the full {@link DeliveryResponse}
 * list, since the point is "how did the batch go," not re-fetching every Delivery the caller already
 * knows the id of. {@code errors} counts replays that threw (e.g. the Event or Subscription they
 * referenced was hard-deleted between listing and replaying) rather than letting one bad id abort
 * the rest of the batch.
 */
public record BulkReplayResponse(int attempted, int succeeded, int stillDead, int errors) {
}
