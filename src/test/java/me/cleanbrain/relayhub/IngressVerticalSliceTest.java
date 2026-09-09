package me.cleanbrain.relayhub;

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
import org.springframework.test.context.ActiveProfiles;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the single-Target slice of the Spec 001 acceptance scenario
 * (docs/product/overview.md "V1 experience", specs/001-push-event-delivery/spec.md):
 * register Source/Source Event/Target/Subscription, POST a Source payload to the
 * generated Ingress URL, and confirm RelayHub extracts, maps, and delivers it.
 * Retry/DLQ/Replay for a second, failing Target is deferred to a later spec/test —
 * see docs/product/goals.md ("Long-term direction", Reliability phase).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IngressVerticalSliceTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        wireMockServer.stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void deliversIngressedEventToSubscribedTarget() {
        String baseUrl = "http://localhost:" + port;

        postJson(baseUrl + "/api/sources", """
                {"key":"demo-source","name":"Demo Source","description":"Acceptance scenario source"}
                """);

        postJson(baseUrl + "/api/sources/demo-source/events", """
                {
                  "key":"customer-created",
                  "name":"Customer Created",
                  "description":"Customer created event",
                  "resourceType":"customer",
                  "operation":"CREATED",
                  "resourceIdPath":"$.customerNo"
                }
                """);

        postJson(baseUrl + "/api/targets", ("""
                {"key":"demo-target-a","name":"Demo Target A","description":"Acceptance scenario target","baseUrl":"%s"}
                """).formatted(wireMockServer.baseUrl()));

        postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"demo-source",
                  "sourceEventKey":"customer-created",
                  "targetKey":"demo-target-a",
                  "name":"Demo Subscription",
                  "description":"Routes customer-created to Target A",
                  "targetMethod":"POST",
                  "targetPath":"/webhook",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\",\\"dealerName\\":\\"${$.name}\\",\\"active\\":true}"
                }
                """);

        ResponseEntity<String> ingressResponse = postJson(baseUrl + "/ingress/v1/demo-source/customer-created", """
                {"customerNo":"C10001","name":"ABC Dealer","email":"dealer@example.com"}
                """);

        assertThat(ingressResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(ingressResponse.getBody()).contains("\"deliveryCount\":1");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/webhook"))
                .withRequestBody(matchingJsonPath("$.dealerId", equalTo("C10001")))
                .withRequestBody(matchingJsonPath("$.dealerName", equalTo("ABC Dealer")))
                .withRequestBody(matchingJsonPath("$.active", equalTo("true"))));
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
}
