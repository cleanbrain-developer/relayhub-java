package me.cleanbrain.relayhub.live;

import java.time.Instant;

/**
 * One edge traversal in the topology view: either "ingress" (Source -&gt; RelayHub, {@code targetKey}
 * null) or "delivery" (RelayHub -&gt; Target, {@code status} set). The admin console's live topology
 * page (Spec 005 follow-up) animates a pulse along the matching edge for each event received.
 */
public record LiveEvent(String stage, String sourceKey, String targetKey, String status, Instant at) {

    public static LiveEvent ingress(String sourceKey) {
        return new LiveEvent("ingress", sourceKey, null, null, Instant.now());
    }

    public static LiveEvent delivery(String sourceKey, String targetKey, boolean success) {
        return new LiveEvent("delivery", sourceKey, targetKey, success ? "success" : "failed", Instant.now());
    }
}
