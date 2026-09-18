package me.cleanbrain.relayhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retry-before-DLQ threshold and the DLQ auto-replay interval were both hardcoded
 * (DeliveryService.MAX_ATTEMPTS, relayhub.dlq.auto-replay-interval-ms) — invisible to operators
 * and only changeable via a redeploy (maintainer request 2026-09-18). This covers the combined
 * GET (public) / PUT (admin-only, both fields range-validated) API, plus an end-to-end check that
 * DeliveryService.deliver() actually reads the configured maxAttempts rather than a stale
 * constant. DlqAutoReplaySchedulerTest covers the interval side end-to-end.
 *
 * <p>Resets both fields back to their defaults in @AfterEach — the H2 test database is shared
 * across test classes in the same run (DB_CLOSE_DELAY=-1), so leaving them mutated would make
 * DlqReplayIdempotencyTest's "attemptCount == 3" assertion (and the auto-replay interval other
 * tests implicitly rely on staying at the 1-hour test-profile default) order-dependent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DeliverySettingsApiTest {

    private static final String DEFAULT_BODY = "{\"maxAttempts\":3,\"autoReplayIntervalMs\":3600000}";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void resetToDefault() {
        String baseUrl = "http://localhost:" + port;
        restTemplate.withBasicAuth("admin", "admin")
                .exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                        new HttpEntity<>(DEFAULT_BODY, jsonHeaders()), String.class);
    }

    @Test
    void defaultsMatchApplicationTestYmlAndArePubliclyReadable() {
        String baseUrl = "http://localhost:" + port;
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/api/delivery-settings", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"maxAttempts\":3").contains("\"autoReplayIntervalMs\":3600000");
    }

    @Test
    void updateRequiresAuthAndValidatesBothFields() throws Exception {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");

        ResponseEntity<String> unauthenticated = restTemplate.exchange(baseUrl + "/api/delivery-settings",
                HttpMethod.PUT, new HttpEntity<>("{\"maxAttempts\":5,\"autoReplayIntervalMs\":60000}", jsonHeaders()),
                String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> attemptsTooLow = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":0,\"autoReplayIntervalMs\":60000}", jsonHeaders()), String.class);
        assertThat(attemptsTooLow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> attemptsTooHigh = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":11,\"autoReplayIntervalMs\":60000}", jsonHeaders()), String.class);
        assertThat(attemptsTooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> intervalTooLow = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":5,\"autoReplayIntervalMs\":1000}", jsonHeaders()), String.class);
        assertThat(intervalTooLow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> intervalTooHigh = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":5,\"autoReplayIntervalMs\":7200000}", jsonHeaders()), String.class);
        assertThat(intervalTooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> updated = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":5,\"autoReplayIntervalMs\":60000}", jsonHeaders()), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedJson = objectMapper.readTree(updated.getBody());
        assertThat(updatedJson.get("maxAttempts").asInt()).isEqualTo(5);
        assertThat(updatedJson.get("autoReplayIntervalMs").asLong()).isEqualTo(60000);

        ResponseEntity<String> reread = restTemplate.getForEntity(baseUrl + "/api/delivery-settings", String.class);
        assertThat(reread.getBody()).contains("\"maxAttempts\":5").contains("\"autoReplayIntervalMs\":60000");
    }

    @Test
    void deliveryServiceActuallyHonorsTheConfiguredMaxAttempts() {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");

        admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":1,\"autoReplayIntervalMs\":3600000}", jsonHeaders()), String.class);

        postJson(admin, baseUrl + "/api/sources", """
                {"key":"settings-verify-source","name":"Settings Verify Source","description":"x"}
                """);
        postJson(admin, baseUrl + "/api/sources/settings-verify-source/events", """
                {"key":"created","name":"Created","description":"x","resourceType":"thing","operation":"CREATED","resourceIdPath":"$.id"}
                """);
        postJson(admin, baseUrl + "/api/targets", """
                {"key":"settings-verify-target","name":"Settings Verify Target","description":"x","baseUrl":"http://localhost:1"}
                """);
        postJson(admin, baseUrl + "/api/subscriptions", """
                {"sourceKey":"settings-verify-source","sourceEventKey":"created","targetKey":"settings-verify-target",
                 "name":"Settings Verify Subscription","description":"x","targetMethod":"POST","targetPath":"/webhook",
                 "targetPayloadTemplate":"{}"}
                """);

        ResponseEntity<String> ingressResponse = postJson(restTemplate,
                baseUrl + "/ingress/v1/settings-verify-source/created", """
                {"id":"s-1"}
                """);
        assertThat(ingressResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<String> deliveries = admin.getForEntity(baseUrl + "/api/deliveries?state=DEAD", String.class);
            JsonNode list = uncheckedRead(deliveries.getBody());
            assertThat(list).isNotEmpty();
            JsonNode delivery = list.get(0);
            assertThat(delivery.get("attemptCount").asInt())
                    .as("maxAttempts=1 should mean exactly one attempt before DEAD, not the old hardcoded 3")
                    .isEqualTo(1);
        });
    }

    private JsonNode uncheckedRead(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<String> postJson(TestRestTemplate client, String url, String body) {
        ResponseEntity<String> response = client.postForEntity(url, new HttpEntity<>(body, jsonHeaders()), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", url, response.getBody())
                .isTrue();
        return response;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
