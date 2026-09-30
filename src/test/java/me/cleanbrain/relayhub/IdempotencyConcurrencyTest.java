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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves db/migration/V11__idempotency_db_constraint.sql actually closes the race the app-level
 * SELECT-then-INSERT check in IngressService can't: N concurrent requests carrying the same
 * (source_event_id, idempotency_key) must produce exactly one Event and exactly one Delivery per
 * Subscription, never more — even though every request's own SELECT can plausibly see "no existing
 * Event yet" before any of them commits. Real Postgres (Testcontainers), not H2 — the partial
 * unique index this test exercises is Postgres-specific DDL the H2 test profile never runs (see
 * ADR-0004), same reasoning as PostgresKafkaIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IdempotencyConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 8;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("relayhub")
            .withUsername("relayhub")
            .withPassword("relayhub");

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
    void concurrentDuplicateIngressRequestsProduceExactlyOneEventAndDelivery() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(200).withBody("ok")));

        postJson(baseUrl + "/api/sources", """
                {"key":"race-source","name":"Race Source","description":"Idempotency race test"}
                """);
        postJson(baseUrl + "/api/sources/race-source/events", """
                {
                  "key":"customer-created","name":"Customer Created","description":"x",
                  "resourceType":"customer","operation":"CREATED",
                  "resourceIdPath":"$.customerNo","idempotencyKeyPath":"$.customerNo"
                }
                """);
        postJson(baseUrl + "/api/targets", ("""
                {"key":"race-target","name":"Race Target","description":"x","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));
        postJson(baseUrl + "/api/targets/race-target/endpoints", """
                {"key":"webhook","name":"Webhook","description":"x","httpMethod":"POST","path":"/webhook"}
                """);
        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"race-source","sourceEventKey":"customer-created","targetKey":"race-target",
                  "targetEndpointKey":"webhook","name":"Race Sub","description":"x",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\"}"
                }
                """);

        // Every request carries the SAME idempotency key (customerNo) — fired truly concurrently
        // via a fixed thread pool sized to match, not sequentially, so their app-level dedup SELECTs
        // genuinely race instead of naturally serializing on one HTTP client thread.
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            List<Callable<ResponseEntity<String>>> tasks = IntStream.range(0, CONCURRENT_REQUESTS)
                    .<Callable<ResponseEntity<String>>>mapToObj(i -> () -> restTemplate.postForEntity(
                            baseUrl + "/ingress/v1/race-source/customer-created",
                            new HttpEntity<>("{\"customerNo\":\"C-RACE-1\",\"name\":\"Race Dealer\"}", jsonHeaders()),
                            String.class))
                    .collect(Collectors.toList());
            List<Future<ResponseEntity<String>>> futures = pool.invokeAll(tasks);

            List<ResponseEntity<String>> responses = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            // Every request must still get a successful, well-formed response (200 or 202) — a
            // request that lost the race is recovered via IngressService's DataIntegrityViolationException
            // catch block, not left to 500.
            for (ResponseEntity<String> response : responses) {
                assertThat(response.getStatusCode().is2xxSuccessful())
                        .as("every concurrent request should succeed (either created or deduplicated): %s", response.getBody())
                        .isTrue();
            }

            // All responses resolve to the SAME Event id.
            List<String> eventIds = responses.stream()
                    .map(r -> uncheckedRead(r.getBody()).get("eventId").asText())
                    .distinct()
                    .toList();
            assertThat(eventIds).as("all concurrent requests must resolve to one Event").hasSize(1);
            String eventId = eventIds.get(0);

            // Exactly one of the N responses actually created the Event (202); the rest deduplicated (200).
            long createdCount = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.ACCEPTED).count();
            assertThat(createdCount).as("exactly one request should have actually created the Event").isEqualTo(1);

            // Exactly one Delivery for this (Event, Subscription) pair reaches the Target — not N.
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                JsonNode deliveries = uncheckedRead(getBody(baseUrl + "/api/deliveries?eventId=" + eventId));
                assertThat(deliveries).hasSize(1);
                assertThat(deliveries.get(0).get("state").asText()).isEqualTo("SUCCEEDED");
            });
        } finally {
            pool.shutdown();
        }
        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    private JsonNode uncheckedRead(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getBody(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> postJson(String url, String body) {
        HttpHeaders headers = jsonHeaders();
        TestRestTemplate client = url.contains("/api/") ? restTemplate.withBasicAuth("admin", "admin") : restTemplate;
        ResponseEntity<String> response = client.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", url, response.getBody())
                .isTrue();
        return response;
    }
}
