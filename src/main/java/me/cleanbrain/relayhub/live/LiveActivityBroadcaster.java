package me.cleanbrain.relayhub.live;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans one {@link LiveEvent} out to every connected browser tab's SSE stream — the "screen
 * control"-style live topology view (Spec 005 follow-up, modeled after Jennifer APM's real-time
 * request map) needs push, not the 30s poll the metrics charts use. Emitters are held in-memory
 * only: a restart drops all connections, and each browser tab's EventSource auto-reconnects on
 * its own (standard SSE behavior), so nothing durable is lost — this is a live view, not a log.
 *
 * <p>Routed through Kafka rather than a direct in-process fan-out (self-review finding,
 * 2026-09-17): {@code deployment.yaml} runs a single replica today, so a direct in-memory
 * broadcast already "worked" — but only by accident of scale. With more than one replica, a
 * browser connected to pod A would never see an event whose {@code broadcast()} call happened to
 * run on pod B, since {@code emitters} is per-instance. Every instance now both publishes to
 * {@link #TOPIC} and consumes it back with its own randomly-generated {@code groupId} (so it is
 * always alone in its own consumer group and therefore receives every message on every partition,
 * not a load-balanced share of them) — a cheap way to turn "broadcast" into an actual cross-pod
 * broadcast using the Kafka cluster this app already depends on, instead of introducing a new
 * pub/sub component (e.g. Redis) purely for this.
 */
@Component
@RequiredArgsConstructor
public class LiveActivityBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveActivityBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MILLIS = 5 * 60 * 1000; // reconnect every 5 min
    public static final String TOPIC = "relayhub.live-events";

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /** Publishes to Kafka — {@link #onLiveEvent} (this same class, one instance per pod) is what
     *  actually fans it out to this pod's local {@link #emitters}, including this pod's own. */
    public void broadcast(LiveEvent event) {
        kafkaTemplate.send(TOPIC, event);
    }

    // A random groupId per JVM instance (evaluated once at listener-container startup, not per
    // message) guarantees this consumer is never in the same group as another pod's, so Kafka
    // gives it every message instead of load-balancing partitions across a shared group — the
    // fan-out semantics this needs, using a mechanism built for the opposite (load-balancing).
    // The default JSON deserializer's type mapping (application.yml) points at
    // DeliveryTaskMessage for DeliveryWorker's own listener; overridden here per-listener since
    // this topic carries LiveEvent instead.
    @KafkaListener(
            topics = TOPIC,
            groupId = "#{T(java.util.UUID).randomUUID().toString()}",
            properties = {
                    "spring.json.value.default.type=me.cleanbrain.relayhub.live.LiveEvent",
                    "spring.json.trusted.packages=me.cleanbrain.relayhub.live"
            })
    public void onLiveEvent(LiveEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("activity").data(event));
            } catch (IOException | IllegalStateException e) {
                // Dead connection (closed tab, network drop) — its own onError/onCompletion
                // callback removes it from `emitters`; nothing further to do here.
                log.debug("Dropping a dead SSE emitter: {}", e.getMessage());
            }
        }
    }
}
