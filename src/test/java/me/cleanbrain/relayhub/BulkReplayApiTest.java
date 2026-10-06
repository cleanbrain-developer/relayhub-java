package me.cleanbrain.relayhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * No bulk DLQ action existed at all before this (scale-out readiness review, 2026-10-06 finding):
 * every DEAD Delivery had to be replayed one click at a time, even when an operator wanted to clear
 * a whole Target's backlog at once after fixing the underlying problem. Covers the new
 * {@code POST /api/deliveries/bulk-replay} endpoint: an unscoped call replays every DEAD Delivery,
 * a {@code targetId}-scoped call only replays that Target's own, and the response tallies
 * succeeded/stillDead accurately.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class BulkReplayApiTest {

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
        restTemplate.withBasicAuth("admin", "admin")
                .exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                        new HttpEntity<>("{\"maxAttempts\":3,\"autoReplayIntervalMs\":3600000}", jsonHeaders()),
                        String.class);
    }

    @Test
    void unscopedBulkReplayRecoversEveryDeadDeliveryAndScopedCallOnlyRecoversItsOwnTarget() {
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");
        admin.exchange(baseUrl + "/api/delivery-settings", HttpMethod.PUT,
                new HttpEntity<>("{\"maxAttempts\":1,\"autoReplayIntervalMs\":3600000}", jsonHeaders()), String.class);

        // Both webhooks start failing so both pairings land in DEAD on their first attempt.
        wireMockServer.stubFor(post(urlEqualTo("/webhook-bulk-a")).willReturn(aResponse().withStatus(500)));
        wireMockServer.stubFor(post(urlEqualTo("/webhook-bulk-b")).willReturn(aResponse().withStatus(500)));

        String targetAId = setUpPairing(admin, "bulk-a", "/webhook-bulk-a");
        String targetBId = setUpPairing(admin, "bulk-b", "/webhook-bulk-b");

        postJson(restTemplate, baseUrl + "/ingress/v1/bulk-a-source/created", """
                {"id":"bulk-a-1"}
                """);
        postJson(restTemplate, baseUrl + "/ingress/v1/bulk-b-source/created", """
                {"id":"bulk-b-1"}
                """);

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(countByState(admin, "targetId=" + targetAId, "DEAD")).isEqualTo(1);
            assertThat(countByState(admin, "targetId=" + targetBId, "DEAD")).isEqualTo(1);
        });

        // Fix Target A only, then bulk-replay scoped to Target A — Target B must stay untouched.
        wireMockServer.stubFor(post(urlEqualTo("/webhook-bulk-a")).willReturn(aResponse().withStatus(200)));

        ResponseEntity<String> scopedReplay = admin.postForEntity(
                baseUrl + "/api/deliveries/bulk-replay?targetId=" + targetAId, null, String.class);
        assertThat(scopedReplay.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode scopedResult = uncheckedRead(scopedReplay.getBody());
        assertThat(scopedResult.get("attempted").asInt()).isEqualTo(1);
        assertThat(scopedResult.get("succeeded").asInt()).isEqualTo(1);
        assertThat(scopedResult.get("stillDead").asInt()).isEqualTo(0);
        assertThat(scopedResult.get("errors").asInt()).isEqualTo(0);

        assertThat(countByState(admin, "targetId=" + targetAId, "SUCCEEDED")).isEqualTo(1);
        assertThat(countByState(admin, "targetId=" + targetBId, "DEAD"))
                .as("scoped bulk-replay must not touch a different Target's Delivery")
                .isEqualTo(1);

        // Now fix Target B too and bulk-replay unscoped — only the one remaining DEAD Delivery is left.
        wireMockServer.stubFor(post(urlEqualTo("/webhook-bulk-b")).willReturn(aResponse().withStatus(200)));

        ResponseEntity<String> unscopedReplay = admin.postForEntity(baseUrl + "/api/deliveries/bulk-replay", null, String.class);
        JsonNode unscopedResult = uncheckedRead(unscopedReplay.getBody());
        assertThat(unscopedResult.get("attempted").asInt()).isEqualTo(1);
        assertThat(unscopedResult.get("succeeded").asInt()).isEqualTo(1);

        assertThat(countByState(admin, "targetId=" + targetBId, "SUCCEEDED")).isEqualTo(1);
    }

    private long countByState(TestRestTemplate admin, String scopeQuery, String state) {
        ResponseEntity<String> response = admin.getForEntity(
                baseUrl + "/api/deliveries?" + scopeQuery + "&state=" + state, String.class);
        return uncheckedRead(response.getBody()).size();
    }

    private String setUpPairing(TestRestTemplate admin, String prefix, String path) {
        postJson(admin, baseUrl + "/api/sources", """
                {"key":"%s-source","name":"%s source","description":"x"}
                """.formatted(prefix, prefix));
        postJson(admin, baseUrl + "/api/sources/%s-source/events".formatted(prefix), """
                {"key":"created","name":"Created","description":"x","resourceType":"thing","operation":"CREATED","resourceIdPath":"$.id"}
                """);
        ResponseEntity<String> targetResponse = postJson(admin, baseUrl + "/api/targets", ("""
                {"key":"%s-target","name":"%s target","description":"x","baseUrl":"%s"}
                """).formatted(prefix, prefix, wireMockServer.baseUrl()));
        postJson(admin, baseUrl + "/api/targets/%s-target/endpoints".formatted(prefix), ("""
                {"key":"webhook","name":"Webhook","description":"x","httpMethod":"POST","path":"%s"}
                """).formatted(path));
        postJson(admin, baseUrl + "/api/subscriptions", """
                {"sourceKey":"%s-source","sourceEventKey":"created","targetKey":"%s-target",
                 "targetEndpointKey":"webhook","name":"%s subscription","description":"x",
                 "targetPayloadTemplate":"{}"}
                """.formatted(prefix, prefix, prefix));
        return uncheckedRead(targetResponse.getBody()).get("id").asText();
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
