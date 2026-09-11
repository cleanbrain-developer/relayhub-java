package me.cleanbrain.relayhub.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Thin proxy to Prometheus's HTTP API — the admin console's browser JS never talks to Prometheus
 * directly, since that Service has no public hostname/HTTPRoute (see cleanbrain-me-infra's
 * kubernetes/apps/relayhub-java/prometheus/). Only the two read-only range/instant query
 * endpoints are exposed, forwarding query params as-is and returning Prometheus's own JSON
 * response body unmodified — no need to re-model it, the frontend chart component consumes
 * Prometheus's format directly. Public, same as every other GET (see security/SecurityConfig.java)
 * — this is observability data, not a write.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    @Value("${relayhub.prometheus.base-url}")
    private String prometheusBaseUrl;

    private final RestClient restClient = RestClient.create();

    @GetMapping("/query_range")
    public ResponseEntity<String> queryRange(@RequestParam String query,
                                              @RequestParam String start,
                                              @RequestParam String end,
                                              @RequestParam String step) {
        String body = restClient.get()
                .uri(prometheusBaseUrl + "/api/v1/query_range?query={query}&start={start}&end={end}&step={step}",
                        query, start, end, step)
                .retrieve()
                .body(String.class);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping("/query")
    public ResponseEntity<String> query(@RequestParam String query) {
        String body = restClient.get()
                .uri(prometheusBaseUrl + "/api/v1/query?query={query}", query)
                .retrieve()
                .body(String.class);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
