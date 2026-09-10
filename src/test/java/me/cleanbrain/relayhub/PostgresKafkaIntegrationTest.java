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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the combined Spec 001/002/003 acceptance scenario against real Postgres and real Kafka
 * (Testcontainers), not H2/@EmbeddedKafka. Exists because three real bugs — the Postgres @Lob/OID
 * issue (Spec 001), the Delivery.createdAt flush-timing issue (Spec 002), and the H2 cross-context
 * teardown (Spec 003) — were each caught only by manual docker-compose verification, not by the
 * fast in-memory test suite. This test is the automated version of that manual check. See
 * docs/status/current-state.md ("Next") and specs/003-kafka-outbox/spec.md.
 *
 * <p>Requires a running Docker daemon. Does not use the "test" Spring profile (which forces H2) —
 * the datasource and Kafka bootstrap-servers properties are overridden to point at the containers,
 * so every other application.yml setting (ddl-auto: update, globally_quoted_identifiers, etc.)
 * runs exactly as it does in production, unlike the H2 test profile's separate settings.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PostgresKafkaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("relayhub")
            .withUsername("relayhub")
            .withPassword("relayhub");

    // confluentinc/cp-kafka, not apache/kafka (used by docker-compose.yml): Testcontainers'
    // KafkaContainer relies on cp-kafka's Docker entrypoint/scripts specifically — apache/kafka's
    // image has a different layout and exits immediately (code 127) under it. Both speak the same
    // Kafka wire protocol, so this is still a real broker for what this test verifies.
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

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
    void fullScenarioAgainstRealPostgresAndKafka() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook-a")).willReturn(aResponse().withStatus(200).withBody("ok")));
        wireMockServer.stubFor(post(urlEqualTo("/webhook-b")).willReturn(aResponse().withStatus(500).withBody("boom")));

        postJson(baseUrl + "/api/sources", """
                {"key":"tc-source","name":"Testcontainers Source","description":"Real Postgres+Kafka verification"}
                """);

        postJson(baseUrl + "/api/sources/tc-source/events", """
                {
                  "key":"customer-created","name":"Customer Created","description":"Customer created event",
                  "resourceType":"customer","operation":"CREATED",
                  "resourceIdPath":"$.customerNo","idempotencyKeyPath":"$.customerNo"
                }
                """);

        postJson(baseUrl + "/api/targets", ("""
                {"key":"tc-target-a","name":"TC Target A","description":"Always succeeds","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));
        postJson(baseUrl + "/api/targets", ("""
                {"key":"tc-target-b","name":"TC Target B","description":"Fails then recovers","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));

        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"tc-source","sourceEventKey":"customer-created","targetKey":"tc-target-a",
                  "name":"Sub A","description":"Routes to A","targetMethod":"POST","targetPath":"/webhook-a",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\"}"
                }
                """);
        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"tc-source","sourceEventKey":"customer-created","targetKey":"tc-target-b",
                  "name":"Sub B","description":"Routes to B","targetMethod":"POST","targetPath":"/webhook-b",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\"}"
                }
                """);

        ResponseEntity<String> ingressResponse = postJson(baseUrl + "/ingress/v1/tc-source/customer-created", """
                {"customerNo":"C50001","name":"JKL Dealer"}
                """);
        assertThat(ingressResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode ingressBody = objectMapper.readTree(ingressResponse.getBody());
        String eventId = ingressBody.get("eventId").asText();
        assertThat(ingressBody.get("deliveryCount").asInt()).isEqualTo(2);

        String targetAId = objectMapper.readTree(getBody(baseUrl + "/api/targets/tc-target-a")).get("id").asText();
        AtomicReference<String> deliveryIdBRef = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode deliveries = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
            assertThat(deliveries).hasSize(2);
            for (JsonNode delivery : deliveries) {
                if (targetAId.equals(delivery.get("targetId").asText())) {
                    assertThat(delivery.get("state").asText()).isEqualTo("SUCCEEDED");
                    // Verifies the Spec 002 createdAt/saveAndFlush fix holds against real Postgres.
                    assertThat(delivery.get("createdAt").asText()).isNotBlank();
                } else {
                    deliveryIdBRef.set(delivery.get("id").asText());
                    assertThat(delivery.get("state").asText()).isEqualTo("DEAD");
                    assertThat(delivery.get("attemptCount").asInt()).isEqualTo(3);
                }
            }
        });

        String deliveryIdB = deliveryIdBRef.get();
        assertThat(deliveryIdB).isNotNull();

        wireMockServer.stubFor(post(urlEqualTo("/webhook-b")).willReturn(aResponse().withStatus(200).withBody("ok")));
        ResponseEntity<String> replayResponse = restTemplate.withBasicAuth("admin", "admin")
                .postForEntity(baseUrl + "/api/deliveries/" + deliveryIdB + "/replay", null, String.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode replayed = objectMapper.readTree(replayResponse.getBody());
        assertThat(replayed.get("state").asText()).isEqualTo("SUCCEEDED");
        assertThat(replayed.get("attemptCount").asInt()).isEqualTo(4);

        ResponseEntity<String> duplicateResponse = postJson(baseUrl + "/ingress/v1/tc-source/customer-created", """
                {"customerNo":"C50001","name":"JKL Dealer"}
                """);
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode duplicateBody = objectMapper.readTree(duplicateResponse.getBody());
        assertThat(duplicateBody.get("eventId").asText()).isEqualTo(eventId);
        assertThat(duplicateBody.get("deduplicated").asBoolean()).isTrue();

        JsonNode deliveriesAfterDuplicate = objectMapper.readTree(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
        assertThat(deliveriesAfterDuplicate).hasSize(2);
    }

    // See IngressVerticalSliceTest's identical helper for why /api/** gets credentials and
    // /ingress/v1/** deliberately doesn't.
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
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("GET %s failed: %s", url, response.getBody())
                .isTrue();
        return response.getBody();
    }
}
