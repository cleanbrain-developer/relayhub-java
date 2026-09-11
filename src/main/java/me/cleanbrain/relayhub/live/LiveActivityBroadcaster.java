package me.cleanbrain.relayhub.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class LiveActivityBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveActivityBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MILLIS = 5 * 60 * 1000; // reconnect every 5 min

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(LiveEvent event) {
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
