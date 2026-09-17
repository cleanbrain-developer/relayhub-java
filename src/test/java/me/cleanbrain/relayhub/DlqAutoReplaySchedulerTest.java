package me.cleanbrain.relayhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import me.cleanbrain.relayhub.delivery.DlqAutoReplayScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * DlqAutoReplayScheduler and DlqScheduleController had zero test coverage (self-review finding,
 * 2026-09-17) despite being the DLQ auto-replay feature the Live page's countdown/pulse UI
 * depends on. Calls the scheduler bean's method directly rather than waiting on its real 30s
 * @Scheduled interval — the interval itself is DlqScheduleController's concern, not this
 * scheduler's replay logic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DlqAutoReplaySchedulerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    DlqAutoReplayScheduler scheduler;

    WireMockServer wireMockServer;
    String baseUrl;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        baseUrl = "http://localhost:" + port;
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void autoReplaySweepRecoversADeadDelivery_andScheduleControllerReflectsIt() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(500).withBody("boom")));

        postJson(baseUrl + "/api/sources", """
                {"key":"dlq-scheduler-source","name":"DLQ Scheduler Source","description":"x"}
                """);
        postJson(baseUrl + "/api/sources/dlq-scheduler-source/events", """
                {"key":"created","name":"Created","description":"x","resourceType":"thing",
                 "operation":"CREATED","resourceIdPath":"$.id"}
                """);
        postJson(baseUrl + "/api/targets", ("""
                {"key":"dlq-scheduler-target","name":"DLQ Scheduler Target","description":"Fails then recovers","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));
        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"dlq-scheduler-source","sourceEventKey":"created","targetKey":"dlq-scheduler-target",
                  "name":"Sub","description":"x","targetMethod":"POST","targetPath":"/webhook",
                  "targetPayloadTemplate":"{\\"id\\":\\"${$.id}\\"}"
                }
                """);

        ResponseEntity<String> ingressResponse = postJson(baseUrl + "/ingress/v1/dlq-scheduler-source/created", """
                {"id":"thing-1"}
                """);
        String eventId = objectMapper.readTree(ingressResponse.getBody()).get("eventId").asText();

        // Wait for the original (synchronous-retry, async-triggered) delivery to exhaust its 3
        // attempts and land DEAD, same as DlqReplayIdempotencyTest's own scenario.
        AtomicDeliveryId deliveryId = new AtomicDeliveryId();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode deliveries = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
            assertThat(deliveries).hasSize(1);
            assertThat(deliveries.get(0).get("state").asText()).isEqualTo("DEAD");
            deliveryId.value = deliveries.get(0).get("id").asText();
        });

        Instant lastRunBefore = scheduler.getLastRunAt();

        // Target recovers; the next auto-replay sweep should pick this DEAD delivery up on its
        // own — no manual Replay call, unlike DlqReplayIdempotencyTest.
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(200).withBody("ok")));
        scheduler.replayDeadDeliveries();

        assertThat(scheduler.getLastRunAt()).isAfter(lastRunBefore);

        JsonNode deliveryAfterSweep = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId)).get(0);
        assertThat(deliveryAfterSweep.get("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(deliveryAfterSweep.get("attemptCount").asInt()).isEqualTo(4);

        // DlqScheduleController: publicly readable, reflects this scheduler's own state — not a
        // separate source of truth that could drift from it.
        JsonNode schedule = objectMapper.readTree(getBody(baseUrl + "/api/dlq/schedule"));
        assertThat(schedule.get("intervalMs").asLong()).isEqualTo(scheduler.getIntervalMs());
        Instant lastRunAt = Instant.parse(schedule.get("lastRunAt").asText());
        Instant nextRunAt = Instant.parse(schedule.get("nextRunAt").asText());
        assertThat(nextRunAt).isEqualTo(lastRunAt.plusMillis(scheduler.getIntervalMs()));
    }

    private static final class AtomicDeliveryId {
        volatile String value;
    }

    private ResponseEntity<String> postJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TestRestTemplate client = url.contains("/api/") ? restTemplate.withBasicAuth("admin", "admin") : restTemplate;
        ResponseEntity<String> response = client.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", url, response.getBody())
                .isTrue();
        return response;
    }

    private String getBody(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
