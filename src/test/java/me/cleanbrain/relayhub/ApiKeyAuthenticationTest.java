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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * "API_KEY 인증 실제 적용" (maintainer request 2026-09-30, follow-up to the integration-platform
 * overhaul): {@code AuthenticationType.API_KEY} was declared since Stage 1 but never actually
 * checked or attached anywhere. Now: a Source with {@code authenticationType=API_KEY} requires the
 * {@code X-Api-Key} header (matching its stored {@code authenticationConfig}) on every ingress
 * request; a Target with {@code authenticationType=API_KEY} gets that same header attached on every
 * outbound delivery attempt. {@code NONE} (every existing demo Source/Target) is unaffected —
 * verified implicitly by every other test in this suite continuing to pass unauthenticated.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class ApiKeyAuthenticationTest {

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
    void sourceWithApiKeyAuthRejectsIngressWithoutOrWithWrongHeader_acceptsWithCorrectHeader() throws Exception {
        adminPostJson("/api/sources", """
                {"key":"apikey-source","name":"API Key Source","description":"x",
                 "authenticationType":"API_KEY","authenticationConfig":"secret-abc-123"}
                """);
        adminPostJson("/api/sources/apikey-source/events", """
                {"key":"created","name":"Created","description":"x","resourceType":"thing",
                 "operation":"CREATED","resourceIdPath":"$.id"}
                """);

        String ingressUrl = baseUrl + "/ingress/v1/apikey-source/created";
        HttpHeaders noAuth = new HttpHeaders();
        noAuth.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> withoutHeader = restTemplate.postForEntity(
                ingressUrl, new HttpEntity<>("{\"id\":\"thing-1\"}", noAuth), String.class);
        assertThat(withoutHeader.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders wrongAuth = new HttpHeaders();
        wrongAuth.setContentType(MediaType.APPLICATION_JSON);
        wrongAuth.set("X-Api-Key", "wrong-key");
        ResponseEntity<String> withWrongHeader = restTemplate.postForEntity(
                ingressUrl, new HttpEntity<>("{\"id\":\"thing-1\"}", wrongAuth), String.class);
        assertThat(withWrongHeader.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders correctAuth = new HttpHeaders();
        correctAuth.setContentType(MediaType.APPLICATION_JSON);
        correctAuth.set("X-Api-Key", "secret-abc-123");
        ResponseEntity<String> withCorrectHeader = restTemplate.postForEntity(
                ingressUrl, new HttpEntity<>("{\"id\":\"thing-1\"}", correctAuth), String.class);
        assertThat(withCorrectHeader.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void targetWithApiKeyAuthGetsTheHeaderAttachedOnOutboundDelivery() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/webhook")).willReturn(aResponse().withStatus(200).withBody("ok")));

        adminPostJson("/api/sources", """
                {"key":"apikey-outbound-source","name":"Source","description":"x"}
                """);
        adminPostJson("/api/sources/apikey-outbound-source/events", """
                {"key":"created","name":"Created","description":"x","resourceType":"thing",
                 "operation":"CREATED","resourceIdPath":"$.id"}
                """);
        adminPostJson("/api/targets", ("""
                {"key":"apikey-outbound-target","name":"Target","description":"x","baseUrl":"%s",
                 "authenticationType":"API_KEY","authenticationConfig":"outbound-secret-xyz"}
                """).formatted(wireMockServer.baseUrl()));
        adminPostJson("/api/targets/apikey-outbound-target/endpoints", """
                {"key":"webhook","name":"Webhook","description":"x","httpMethod":"POST","path":"/webhook"}
                """);
        adminPostJson("/api/subscriptions", """
                {
                  "sourceKey":"apikey-outbound-source","sourceEventKey":"created","targetKey":"apikey-outbound-target",
                  "targetEndpointKey":"webhook","name":"Sub","description":"x",
                  "targetPayloadTemplate":"{\\"id\\":\\"${$.id}\\"}"
                }
                """);

        ResponseEntity<String> ingressResponse = restTemplate.postForEntity(
                baseUrl + "/ingress/v1/apikey-outbound-source/created",
                new HttpEntity<>("{\"id\":\"thing-1\"}", jsonHeaders()), String.class);
        assertThat(ingressResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String eventId = objectMapper.readTree(ingressResponse.getBody()).get("eventId").asText();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonNode deliveries = objectMapper.readTree(getBody("/api/deliveries?eventId=" + eventId));
            assertThat(deliveries).hasSize(1);
            assertThat(deliveries.get(0).get("state").asText()).isEqualTo("SUCCEEDED");
        });

        wireMockServer.verify(postRequestedFor(urlEqualTo("/webhook")).withHeader("X-Api-Key", equalTo("outbound-secret-xyz")));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> adminPostJson(String path, String body) {
        HttpHeaders headers = jsonHeaders();
        ResponseEntity<String> response = restTemplate.withBasicAuth("admin", "admin")
                .postForEntity(baseUrl + path, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", path, response.getBody())
                .isTrue();
        return response;
    }

    private String getBody(String path) {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
