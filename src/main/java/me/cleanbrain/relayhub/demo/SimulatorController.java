package me.cleanbrain.relayhub.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Admin-only on/off switch for relayhub-demo-systems' event-generation scheduler (see
 * DemoDataSeeder's Javadoc — RelayHub itself never generates traffic, the simulator does), so the
 * console can pause/resume demo traffic without a redeploy. Same proxy shape as
 * metrics/MetricsController, except gated behind admin auth (see SecurityConfig) since this is a
 * write/control action, not read-only observability data — demo-systems' own /admin/scheduler/*
 * endpoints carry no auth of their own, relying entirely on this being the only door in.
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    @Value("${relayhub.demo.simulator-base-url:http://localhost:9500}")
    private String simulatorBaseUrl;

    private final RestClient restClient = RestClient.create();

    @GetMapping("/status")
    public ResponseEntity<String> status() {
        return proxy(restClient.get().uri(simulatorBaseUrl + "/admin/scheduler"));
    }

    @PostMapping("/pause")
    public ResponseEntity<String> pause() {
        return proxy(restClient.post().uri(simulatorBaseUrl + "/admin/scheduler/pause"));
    }

    @PostMapping("/resume")
    public ResponseEntity<String> resume() {
        return proxy(restClient.post().uri(simulatorBaseUrl + "/admin/scheduler/resume"));
    }

    private ResponseEntity<String> proxy(RestClient.RequestHeadersSpec<?> request) {
        String body = request.retrieve().body(String.class);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
