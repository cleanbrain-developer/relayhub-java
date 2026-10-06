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
 * The Deliveries console's only filter used to be {@code state} (scale-out readiness review,
 * 2026-10-06 finding: an operator chasing "why does Target X keep failing" had to scroll the
 * unfiltered top-200 list by eye). Covers the new {@code targetId}/{@code subscriptionId} query
 * params this review added to {@code DeliveryController#list}, proving each narrows to exactly
 * the right Deliveries and doesn't leak the other pairing's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DeliveryFilterApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void resetMaxAttempts() {
        String baseUrl = "http://localhost:" + port;
        restTemplate.withBasicAuth("admin", "admin")
                .exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                        new HttpEntity<>("{\"maxAttempts\":3,\"autoReplayIntervalMs\":3600000}", jsonHeaders()),
                        String.class);
    }

    @Test
    void targetIdAndSubscriptionIdEachNarrowToTheirOwnPairing() {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");

        // maxAttempts=1 so both pairings reach DEAD on their very first (failing) attempt.
        admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":1,\"autoReplayIntervalMs\":3600000}", jsonHeaders()), String.class);

        String targetAId = setUpPairing(admin, baseUrl, "filter-a");
        String targetBId = setUpPairing(admin, baseUrl, "filter-b");

        postJson(restTemplate, baseUrl + "/ingress/v1/filter-a-source/created", """
                {"id":"a-1"}
                """);
        postJson(restTemplate, baseUrl + "/ingress/v1/filter-b-source/created", """
                {"id":"b-1"}
                """);

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode byTargetA = get(admin, baseUrl + "/api/deliveries?targetId=" + targetAId);
            assertThat(byTargetA).hasSize(1);
            assertThat(byTargetA.get(0).get("targetId").asText()).isEqualTo(targetAId);

            JsonNode byTargetB = get(admin, baseUrl + "/api/deliveries?targetId=" + targetBId);
            assertThat(byTargetB).hasSize(1);
            assertThat(byTargetB.get(0).get("targetId").asText()).isEqualTo(targetBId);
        });

        ResponseEntity<String> subResponse = admin.getForEntity(baseUrl + "/api/subscriptions", String.class);
        JsonNode subs = uncheckedRead(subResponse.getBody());
        String subAId = null;
        for (JsonNode sub : subs) {
            if (sub.get("targetKey").asText().equals("filter-a-target")) {
                subAId = sub.get("id").asText();
            }
        }
        assertThat(subAId).as("Subscription for filter-a-target should exist").isNotNull();

        JsonNode bySubA = get(admin, baseUrl + "/api/deliveries?subscriptionId=" + subAId);
        assertThat(bySubA).hasSize(1);
        assertThat(bySubA.get(0).get("targetId").asText()).isEqualTo(targetAId);

        // state combines with either filter, not just replaces it.
        JsonNode byTargetAAndDeadState = get(admin, baseUrl + "/api/deliveries?targetId=" + targetAId + "&state=DEAD");
        assertThat(byTargetAAndDeadState).hasSize(1);
        JsonNode byTargetAAndSucceededState = get(admin, baseUrl + "/api/deliveries?targetId=" + targetAId + "&state=SUCCEEDED");
        assertThat(byTargetAAndSucceededState).isEmpty();
    }

    /** Registers one Source/Event/Target/Endpoint/Subscription pairing named "{prefix}-*", returns the Target's id. */
    private String setUpPairing(TestRestTemplate admin, String baseUrl, String prefix) {
        postJson(admin, baseUrl + "/api/sources", """
                {"key":"%s-source","name":"%s source","description":"x"}
                """.formatted(prefix, prefix));
        postJson(admin, baseUrl + "/api/sources/%s-source/events".formatted(prefix), """
                {"key":"created","name":"Created","description":"x","resourceType":"thing","operation":"CREATED","resourceIdPath":"$.id"}
                """);
        ResponseEntity<String> targetResponse = postJson(admin, baseUrl + "/api/targets", """
                {"key":"%s-target","name":"%s target","description":"x","baseUrl":"http://localhost:1"}
                """.formatted(prefix, prefix));
        postJson(admin, baseUrl + "/api/targets/%s-target/endpoints".formatted(prefix), """
                {"key":"webhook","name":"Webhook","description":"x","httpMethod":"POST","path":"/webhook"}
                """);
        postJson(admin, baseUrl + "/api/subscriptions", """
                {"sourceKey":"%s-source","sourceEventKey":"created","targetKey":"%s-target",
                 "targetEndpointKey":"webhook","name":"%s subscription","description":"x",
                 "targetPayloadTemplate":"{}"}
                """.formatted(prefix, prefix, prefix));
        return uncheckedRead(targetResponse.getBody()).get("id").asText();
    }

    private JsonNode get(TestRestTemplate client, String url) {
        ResponseEntity<String> response = client.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return uncheckedRead(response.getBody());
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
