package me.cleanbrain.relayhub.live;

import java.time.Instant;

/**
 * One edge traversal in the topology view: either "ingress" (Source -&gt; SourceEvent,
 * {@code targetKey} null) or "delivery" (SourceEvent -&gt; Target, {@code status} set). {@code
 * eventKey} identifies which SourceEvent this traversal belongs to (e.g. "flight-created" vs.
 * "flight-status-updated") so the console's live topology can route the pulse through the actual
 * event node instead of collapsing every event under one Source into a single edge — added
 * because the topology looked misleadingly simpler than the real Subscription graph (see
 * SubscriptionsPage, which shows one row per Source Event -&gt; Target pair). The admin console's
 * live topology page (Spec 005 follow-up) animates a pulse along the matching edge for each event
 * received.
 */
public record LiveEvent(String stage, String sourceKey, String eventKey, String targetKey, String status, Instant at) {

    public static LiveEvent ingress(String sourceKey, String eventKey) {
        return new LiveEvent("ingress", sourceKey, eventKey, null, null, Instant.now());
    }

    public static LiveEvent delivery(String sourceKey, String eventKey, String targetKey, boolean success) {
        return new LiveEvent("delivery", sourceKey, eventKey, targetKey, success ? "success" : "failed", Instant.now());
    }
}
