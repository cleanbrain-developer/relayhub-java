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
 * The retry-before-DLQ threshold was a hardcoded constant (DeliveryService.MAX_ATTEMPTS) that an
 * operator had no way to see or change without a redeploy (maintainer request 2026-09-18). This
 * covers the new GET (public, default 3) / PUT (admin-only, range-validated) API, plus an
 * end-to-end check that DeliveryService.deliver() actually reads the configured value rather than
 * a stale constant.
 *
 * <p>Resets the setting back to the default (3) in @AfterEach — the H2 test database is shared
 * across test classes in the same run (DB_CLOSE_DELAY=-1), so leaving it mutated would make
 * DlqReplayIdempotencyTest's "attemptCount == 3" assertion order-dependent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DeliverySettingsApiTest {

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
                        new HttpEntity<>("{\"maxAttempts\":3}", jsonHeaders()), String.class);
    }

    @Test
    void defaultsToThreeAndIsPublicallyReadable() {
        String baseUrl = "http://localhost:" + port;
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/api/delivery-settings", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"maxAttempts\":3");
    }

    @Test
    void updateRequiresAuthAndIsValidated() throws Exception {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");

        ResponseEntity<String> unauthenticated = restTemplate.exchange(baseUrl + "/api/delivery-settings",
                HttpMethod.PUT, new HttpEntity<>("{\"maxAttempts\":5}", jsonHeaders()), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> tooLow = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":0}", jsonHeaders()), String.class);
        assertThat(tooLow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> tooHigh = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":11}", jsonHeaders()), String.class);
        assertThat(tooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> updated = admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":5}", jsonHeaders()), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedJson = objectMapper.readTree(updated.getBody());
        assertThat(updatedJson.get("maxAttempts").asInt()).isEqualTo(5);

        ResponseEntity<String> reread = restTemplate.getForEntity(baseUrl + "/api/delivery-settings", String.class);
        assertThat(reread.getBody()).contains("\"maxAttempts\":5");
    }

    @Test
    void deliveryServiceActuallyHonorsTheConfiguredValue() {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");

        admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":1}", jsonHeaders()), String.class);

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
