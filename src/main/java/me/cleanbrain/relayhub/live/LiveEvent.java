package me.cleanbrain.relayhub.live;

import java.time.Instant;

/**
 * One edge traversal in the topology view: "ingress" (Source -&gt; SourceEvent, {@code targetKey}
 * null), "delivery" (SourceEvent -&gt; Target, {@code status} set), or "dlq" (RelayHub -&gt; DLQ,
 * broadcast once a Delivery's retries are exhausted and it lands in {@code DeliveryState.DEAD}).
 * {@code eventKey} identifies which SourceEvent this traversal belongs to (e.g. "flight-created"
 * vs. "flight-status-updated") so the console's live topology can route the pulse through the
 * actual event node instead of collapsing every event under one Source into a single edge.
 * {@code replay} marks a "delivery" traversal that came from {@code DeliveryService.replay} (either
 * the admin console's manual Replay button or DlqAutoReplayScheduler) rather than the original
 * delivery attempt, so the console can visually distinguish a retry from first-attempt traffic.
 * The admin console's live topology page (Spec 005 follow-up) animates a pulse along the matching
 * edge for each event received.
 */
public record LiveEvent(
        String stage, String sourceKey, String eventKey, String targetKey, String status, boolean replay, Instant at) {

    public static LiveEvent ingress(String sourceKey, String eventKey) {
        return new LiveEvent("ingress", sourceKey, eventKey, null, null, false, Instant.now());
    }

    public static LiveEvent delivery(String sourceKey, String eventKey, String targetKey, boolean success, boolean replay) {
        return new LiveEvent("delivery", sourceKey, eventKey, targetKey, success ? "success" : "failed", replay, Instant.now());
    }

    public static LiveEvent dlq(String sourceKey, String eventKey, String targetKey) {
        return new LiveEvent("dlq", sourceKey, eventKey, targetKey, "dead", false, Instant.now());
    }
}
