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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reproduces the Spec 002/003 acceptance scenario (specs/002-retry-dlq-replay/spec.md,
 * specs/003-kafka-outbox/spec.md): Target B fails every attempt and lands in DEAD (DLQ) after
 * retry/backoff — now driven asynchronously via Outbox -> Kafka -> DeliveryWorker — an Operator
 * replay (still synchronous) recovers it, and a duplicate ingress request (same idempotency key)
 * does not create a second Event or new Deliveries.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DlqReplayIdempotencyTest {

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
    void targetBFailsRetriesDlqThenReplaySucceeds_andDuplicateIngressIsDeduplicated() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook-a")).willReturn(aResponse().withStatus(200).withBody("ok")));
        wireMockServer.stubFor(post(urlEqualTo("/webhook-b")).willReturn(aResponse().withStatus(500).withBody("boom")));

        postJson(baseUrl + "/api/sources", """
                {"key":"spec002-source","name":"Demo Source","description":"Spec 002 source"}
                """);

        postJson(baseUrl + "/api/sources/spec002-source/events", """
                {
                  "key":"customer-created","name":"Customer Created","description":"Customer created event",
                  "resourceType":"customer","operation":"CREATED",
                  "resourceIdPath":"$.customerNo","idempotencyKeyPath":"$.customerNo"
                }
                """);

        postJson(baseUrl + "/api/targets", ("""
                {"key":"spec002-target-a","name":"Demo Target A","description":"Always succeeds","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));
        postJson(baseUrl + "/api/targets", ("""
                {"key":"spec002-target-b","name":"Demo Target B","description":"Fails then recovers","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));

        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"spec002-source","sourceEventKey":"customer-created","targetKey":"spec002-target-a",
                  "name":"Sub A","description":"Routes to Target A",
                  "targetMethod":"POST","targetPath":"/webhook-a",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\"}"
                }
                """);
        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"spec002-source","sourceEventKey":"customer-created","targetKey":"spec002-target-b",
                  "name":"Sub B","description":"Routes to Target B",
                  "targetMethod":"POST","targetPath":"/webhook-b",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\"}"
                }
                """);

        ResponseEntity<String> ingressResponse = postJson(baseUrl + "/ingress/v1/spec002-source/customer-created", """
                {"customerNo":"C10001","name":"ABC Dealer"}
                """);
        assertThat(ingressResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode ingressBody = objectMapper.readTree(ingressResponse.getBody());
        String eventId = ingressBody.get("eventId").asText();
        assertThat(ingressBody.get("deliveryCount").asInt()).isEqualTo(2);
        assertThat(ingressBody.get("deduplicated").asBoolean()).isFalse();

        // Delivery is now asynchronous (Outbox -> Kafka -> DeliveryWorker) — poll until both
        // Deliveries reach a terminal state instead of asserting immediately. See
        // specs/003-kafka-outbox/spec.md.
        String targetAId = objectMapper.readTree(getBody(baseUrl + "/api/targets/spec002-target-a")).get("id").asText();
        AtomicReference<String> deliveryIdARef = new AtomicReference<>();
        AtomicReference<String> deliveryIdBRef = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode deliveries = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
            assertThat(deliveries).hasSize(2);
            for (JsonNode delivery : deliveries) {
                if (targetAId.equals(delivery.get("targetId").asText())) {
                    deliveryIdARef.set(delivery.get("id").asText());
                    assertThat(delivery.get("state").asText()).isEqualTo("SUCCEEDED");
                } else {
                    deliveryIdBRef.set(delivery.get("id").asText());
                    assertThat(delivery.get("state").asText()).isEqualTo("DEAD");
                    assertThat(delivery.get("attemptCount").asInt()).isEqualTo(3);
                }
            }
        });
        String deliveryIdA = deliveryIdARef.get();
        String deliveryIdB = deliveryIdBRef.get();
        assertThat(deliveryIdA).isNotNull();
        assertThat(deliveryIdB).isNotNull();

        JsonNode attemptsB = objectMapper.readTree(getBody(baseUrl + "/api/deliveries/" + deliveryIdB + "/attempts"));
        assertThat(attemptsB).hasSize(3);
        for (JsonNode attempt : attemptsB) {
            assertThat(attempt.get("status").asText()).isEqualTo("FAILED");
        }

        // Target B recovers; replay should now succeed.
        wireMockServer.stubFor(post(urlEqualTo("/webhook-b")).willReturn(aResponse().withStatus(200).withBody("ok")));
        ResponseEntity<String> replayResponse = restTemplate.postForEntity(baseUrl + "/api/deliveries/" + deliveryIdB + "/replay", null, String.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode replayed = objectMapper.readTree(replayResponse.getBody());
        assertThat(replayed.get("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(replayed.get("attemptCount").asInt()).isEqualTo(4);

        // Duplicate ingress with the same idempotency key must not create a new Event or Deliveries.
        ResponseEntity<String> duplicateResponse = postJson(baseUrl + "/ingress/v1/spec002-source/customer-created", """
                {"customerNo":"C10001","name":"ABC Dealer"}
                """);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode duplicateBody = objectMapper.readTree(duplicateResponse.getBody());
        assertThat(duplicateBody.get("eventId").asText()).isEqualTo(eventId);
        assertThat(duplicateBody.get("deliveryCount").asInt()).isZero();
        assertThat(duplicateBody.get("deduplicated").asBoolean()).isTrue();

        JsonNode deliveriesAfterDuplicate = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
        assertThat(deliveriesAfterDuplicate).hasSize(2);
    }

    private ResponseEntity<String> postJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", url, response.getBody())
                .isTrue();
        return response;
    }

    private String getBody(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("GET %s failed: %s", url, response.getBody())
                .isTrue();
        return response.getBody();
    }
}
