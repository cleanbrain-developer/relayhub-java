package me.cleanbrain.relayhub.live;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Public, same as every other GET (see security/SecurityConfig.java) — this is observability data. */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveActivityController {

    private final LiveActivityBroadcaster broadcaster;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
