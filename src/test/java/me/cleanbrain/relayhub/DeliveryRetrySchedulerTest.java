package me.cleanbrain.relayhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import me.cleanbrain.relayhub.delivery.DeliveryRetryScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stage 2 of the integration-platform overhaul (maintainer request 2026-09-30): proves the
 * non-blocking retry engine actually persists {@code RETRYING} + {@code nextAttemptAt} between
 * attempts instead of blocking the caller through a {@code Thread.sleep} loop, and that a replay
 * which fails again re-enters the same retry loop (RETRYING) rather than landing straight back on
 * DEAD when {@code maxAttempts} has since been raised — see DeliveryService.replay's Javadoc for
 * why that specifically matters.
 *
 * <p>Deliberately polls via {@code await()} against the real, unconditionally-running
 * {@link DeliveryRetryScheduler} {@code @Scheduled} tick rather than calling {@code runDueBatch()}
 * directly (unlike {@code DlqAutoReplaySchedulerTest}, whose scheduler self-gates on a 1-hour test-
 * profile interval and so needs manual driving to ever fire within a test). DeliveryRetryScheduler
 * has no such gate — it ticks for real, every second, for this whole test's duration — so a manual
 * call here would race the background tick over who processes the same due delivery first (both
 * read RETRYING before either commits its transition), non-deterministically inflating
 * attemptCount. Polling for the eventual outcome sidesteps that race and is also a more faithful
 * test of the actual production path.
 *
 * <p>A fixed, no-jitter/1x-multiplier DeliveryPolicy is set on the Subscription itself (Stage 1's
 * per-Subscription override fields), with a deliberately generous 3.5s backoff — not a short one.
 * {@link me.cleanbrain.relayhub.delivery.DeliveryRetryScheduler} only ever advances a delivery once
 * its {@code nextAttemptAt} has actually elapsed (see its {@code findTop20By...} query), so
 * correctness here does not depend on catching any particular tick in the act — only on each
 * {@code await()} window being comfortably shorter than the backoff it's checking before, which a
 * short backoff cannot reliably guarantee under CI-runner-speed variance. An earlier version of
 * this test used a 50ms backoff and a 2s first-check window, which was flaky on GitHub's shared
 * runners specifically: a slow HTTP round trip past that first check could let two full ~1s ticks
 * already elapse before the very first poll ever ran, observing DEAD instead of RETRYING(1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DeliveryRetrySchedulerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

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
    void firstAttemptFailure_persistsRetryingWithoutBlocking_thenSchedulerAdvancesToDeadOnExhaustion() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(500).withBody("boom")));

        postJson(baseUrl + "/api/sources", """
                {"key":"retry-scheduler-source","name":"Retry Scheduler Source","description":"x"}
                """);
        postJson(baseUrl + "/api/sources/retry-scheduler-source/events", """
                {"key":"created","name":"Created","description":"x","resourceType":"thing",
                 "operation":"CREATED","resourceIdPath":"$.id"}
                """);
        postJson(baseUrl + "/api/targets", ("""
                {"key":"retry-scheduler-target","name":"Retry Scheduler Target","description":"Always fails","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));
        postJson(baseUrl + "/api/targets/retry-scheduler-target/endpoints", """
                {"key":"webhook","name":"Webhook","description":"x","httpMethod":"POST","path":"/webhook"}
                """);
        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"retry-scheduler-source","sourceEventKey":"created","targetKey":"retry-scheduler-target",
                  "targetEndpointKey":"webhook","name":"Sub","description":"x",
                  "targetPayloadTemplate":"{\\"id\\":\\"${$.id}\\"}",
                  "maxAttempts":3,"initialBackoffMs":3500,"maxBackoffMs":3500,"backoffMultiplier":1.0,"jitter":false
                }
                """);

        ResponseEntity<String> ingressResponse = postJson(baseUrl + "/ingress/v1/retry-scheduler-source/created", """
                {"id":"thing-1"}
                """);
        String eventId = objectMapper.readTree(ingressResponse.getBody()).get("eventId").asText();

        // The first attempt runs synchronously inside deliver() (attemptCount reaches 1 almost
        // immediately) and, on failure, must persist RETRYING + a non-null nextAttemptAt rather
        // than blocking this test's thread through a sleep — this window (2.5s) is comfortably
        // shorter than the 3.5s backoff above, so it's structurally guaranteed to still be
        // RETRYING(1) here regardless of scheduler tick timing (see the class Javadoc).
        await().atMost(Duration.ofSeconds(2, 500_000_000)).untilAsserted(() -> {
            JsonNode deliveries = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
            assertThat(deliveries).as("DeliveryWorker should have created the Delivery by now").isNotEmpty();
            JsonNode delivery = deliveries.get(0);
            assertThat(delivery.get("state").asText()).isEqualTo("RETRYING");
            assertThat(delivery.get("attemptCount").asInt()).isEqualTo(1);
            assertThat(delivery.has("nextAttemptAt")).as("nextAttemptAt should be present while RETRYING").isTrue();
        });

        // The real background DeliveryRetryScheduler tick should carry this the rest of the way to
        // DEAD on its own once each nextAttemptAt elapses — two more hops (attempt2, attempt3),
        // each up to ~3.5s backoff + ~1s scheduler tick lag, so up to ~9s worst case; generous
        // margin above that.
        await().atMost(Duration.ofSeconds(11)).untilAsserted(() -> {
            JsonNode delivery = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId)).get(0);
            assertThat(delivery.get("state").asText()).isEqualTo("DEAD");
            assertThat(delivery.get("attemptCount").asInt()).isEqualTo(3);
            assertThat(delivery.has("nextAttemptAt")).as("nextAttemptAt should be absent once terminal").isFalse();
        });
        String deliveryId = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId)).get(0).get("id").asText();

        // Raise this Subscription's own maxAttempts above what it already exhausted, then replay
        // while the Target is still failing: DeliveryService.replay must re-enter the retry loop
        // (RETRYING) instead of landing straight back on DEAD, since attemptNumber(4) is now below
        // the raised effective maxAttempts(5) -- this is the behavior change from before Stage 2,
        // where replay() always went straight back to DEAD on any failure.
        // GET /api/subscriptions returns every Subscription across every test sharing this cached
        // Spring context/H2 database (see RelayHubApplicationTests), not just this test's own — must
        // filter by this test's unique sourceKey, not blindly take index 0.
        String subscriptionId = null;
        for (JsonNode s : objectMapper.readTree(getBody(baseUrl + "/api/subscriptions"))) {
            if ("retry-scheduler-source".equals(s.get("sourceKey").asText())) {
                subscriptionId = s.get("id").asText();
                break;
            }
        }
        assertThat(subscriptionId).as("this test's own Subscription should exist").isNotNull();
        putJson(baseUrl + "/api/subscriptions/" + subscriptionId, """
                {
                  "name":"Sub","description":"x","targetPayloadTemplate":"{\\"id\\":\\"${$.id}\\"}",
                  "maxAttempts":5,"initialBackoffMs":3500,"maxBackoffMs":3500,"backoffMultiplier":1.0,"jitter":false
                }
                """);

        ResponseEntity<String> replayResponse = restTemplate.withBasicAuth("admin", "admin")
                .postForEntity(baseUrl + "/api/deliveries/" + deliveryId + "/replay", null, String.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode replayed = objectMapper.readTree(replayResponse.getBody());
        assertThat(replayed.get("state").asText()).isEqualTo("RETRYING");
        assertThat(replayed.get("attemptCount").asInt()).isEqualTo(4);

        // Target recovers; the scheduler's next tick should carry it the rest of the way to
        // SUCCEEDED on its own — no second manual replay.
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(200).withBody("ok")));
        await().atMost(Duration.ofSeconds(7)).untilAsserted(() -> {
            JsonNode delivery = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId)).get(0);
            assertThat(delivery.get("state").asText()).isEqualTo("SUCCEEDED");
            assertThat(delivery.get("attemptCount").asInt()).isEqualTo(5);
        });
    }

    private ResponseEntity<String> postJson(String url, String body) {
        return exchange(url, HttpMethod.POST, body);
    }

    private ResponseEntity<String> putJson(String url, String body) {
        return exchange(url, HttpMethod.PUT, body);
    }

    private ResponseEntity<String> exchange(String url, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TestRestTemplate client = url.contains("/api/") ? restTemplate.withBasicAuth("admin", "admin") : restTemplate;
        ResponseEntity<String> response = client.exchange(url, method, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("%s %s failed: %s", method, url, response.getBody())
                .isTrue();
        return response;
    }

    private String getBody(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
