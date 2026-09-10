package me.cleanbrain.relayhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import me.cleanbrain.relayhub.delivery.Delivery;
import me.cleanbrain.relayhub.delivery.DeliveryAttemptRepository;
import me.cleanbrain.relayhub.delivery.DeliveryService;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.subscription.Subscription;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the ADR-0004 fix directly: calling {@link DeliveryService#deliver} twice for the same
 * (Event, Subscription) pair — simulating a Kafka-redelivered {@code DeliveryTaskMessage} after a
 * worker crash before its offset commits — must not create a second {@code Delivery} row or
 * re-run the attempt loop. See specs/003-kafka-outbox/spec.md ("Deliberately out of scope").
 *
 * <p>Same {@code @SpringBootTest}/{@code @EmbeddedKafka} configuration as
 * {@link IngressVerticalSliceTest}/{@link DlqReplayIdempotencyTest} so Spring's test context
 * caching reuses their context instead of starting a separate one that would share the "test"
 * profile's H2 database name with a differently-configured context — see
 * {@link RelayHubApplicationTests}'s comment for why that combination breaks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class DeliveryDedupTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    DeliveryAttemptRepository deliveryAttemptRepository;

    WireMockServer wireMockServer;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void redeliveredTaskDoesNotCreateASecondDeliveryOrReRunAttempts() throws Exception {
        String sourceKey = "dedup-source";
        registerSourceAndEvent(sourceKey);
        String targetKey = "dedup-target";
        registerTarget(targetKey);
        registerSubscription(sourceKey, targetKey);

        UUID eventId = createEvent(sourceKey);
        Event event = eventRepository.findById(eventId).orElseThrow();
        // findActiveWithTargetBySourceEventId (not findBySourceEventIdAndStatus): eagerly loads
        // Subscription.target so it's safe to use here, outside any transaction — deliver() below
        // starts its own transaction, and a lazily-loaded Target from a different, already-closed
        // session cannot be initialized inside it (LazyInitializationException, hit before this fix).
        Subscription subscription = subscriptionRepository.findActiveWithTargetBySourceEventId(
                event.getSourceEventId(), me.cleanbrain.relayhub.common.Status.ACTIVE).get(0);
        JsonNode payload = objectMapper.readTree(event.getPayload());

        Delivery first = deliveryService.deliver(event, subscription, payload);
        assertThat(first.getState().name()).isEqualTo("SUCCEEDED");
        assertThat(deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(first.getId())).hasSize(1);

        // Simulate DeliveryWorker re-consuming the same message (e.g. after a crash before commit).
        Delivery second = deliveryService.deliver(event, subscription, payload);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(first.getId()))
                .as("no second HTTP attempt should have been made")
                .hasSize(1);
    }

    private void registerSourceAndEvent(String sourceKey) {
        postJson("/api/sources", ("""
                {"key":"%s","name":"Dedup Source","description":"Delivery dedup test"}
                """).formatted(sourceKey));
        postJson(("/api/sources/%s/events").formatted(sourceKey), """
                {
                  "key":"customer-created","name":"Customer Created","description":"Customer created event",
                  "resourceType":"customer","operation":"CREATED","resourceIdPath":"$.customerNo"
                }
                """);
    }

    private void registerTarget(String targetKey) {
        postJson("/api/targets", ("""
                {"key":"%s","name":"Dedup Target","description":"Always succeeds","baseUrl":"%s"}
                """).formatted(targetKey, wireMockServer.baseUrl()));
    }

    private void registerSubscription(String sourceKey, String targetKey) {
        postJson("/api/subscriptions", ("""
                {
                  "sourceKey":"%s","sourceEventKey":"customer-created","targetKey":"%s",
                  "name":"Dedup Sub","description":"Routes to dedup target",
                  "targetMethod":"POST","targetPath":"/webhook",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\"}"
                }
                """).formatted(sourceKey, targetKey));
    }

    private UUID createEvent(String sourceKey) throws Exception {
        ResponseEntity<String> response = postJson(("/ingress/v1/%s/customer-created").formatted(sourceKey), """
                {"customerNo":"C60001","name":"MNO Dealer"}
                """);
        return UUID.fromString(objectMapper.readTree(response.getBody()).get("eventId").asText());
    }

    private ResponseEntity<String> postJson(String path, String body) {
        String url = "http://localhost:" + port + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", url, response.getBody())
                .isTrue();
        return response;
    }
}
