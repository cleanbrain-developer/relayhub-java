package me.cleanbrain.relayhub;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SimulatorController had zero test coverage (self-review finding, 2026-09-17) despite proxying a
 * write/control action (pausing real traffic generation) behind admin auth. The simulator's own
 * base URL comes from a fixed @Value property, not a per-test DB record like a Target's baseUrl
 * (see DlqReplayIdempotencyTest), so the WireMock stand-in has to be bound and registered via
 * @DynamicPropertySource before the Spring context starts, not in @BeforeEach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class SimulatorControllerTest {

    static WireMockServer simulatorMock;

    @BeforeAll
    static void startSimulatorMock() {
        simulatorMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        simulatorMock.start();
    }

    @AfterAll
    static void stopSimulatorMock() {
        simulatorMock.stop();
    }

    @DynamicPropertySource
    static void registerSimulatorBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("relayhub.demo.simulator-base-url", simulatorMock::baseUrl);
    }

    @AfterEach
    void resetStubs() {
        simulatorMock.resetAll();
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void statusPauseResumeAllRequireAdminAndProxyTheSimulatorsResponseThrough() {
        String baseUrl = "http://localhost:" + port;
        simulatorMock.stubFor(get(urlEqualTo("/admin/scheduler"))
                .willReturn(okJson("{\"running\":true,\"intervalMs\":5000,\"maxLiveFlights\":8}")));
        simulatorMock.stubFor(post(urlEqualTo("/admin/scheduler/pause"))
                .willReturn(okJson("{\"running\":false}")));
        simulatorMock.stubFor(post(urlEqualTo("/admin/scheduler/resume"))
                .willReturn(okJson("{\"running\":true}")));

        // Unauthenticated -> 401 for all three, including the GET (unlike every other public GET
        // in this app — see SecurityConfig.java's comment on why /api/simulator/** is excepted).
        assertThat(restTemplate.getForEntity(baseUrl + "/api/simulator/status", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity(baseUrl + "/api/simulator/pause", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity(baseUrl + "/api/simulator/resume", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        simulatorMock.verify(0, getRequestedFor(urlEqualTo("/admin/scheduler")));

        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");

        ResponseEntity<String> status = admin.getForEntity(baseUrl + "/api/simulator/status", String.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody()).contains("\"running\":true").contains("\"maxLiveFlights\":8");

        ResponseEntity<String> pause = admin.postForEntity(baseUrl + "/api/simulator/pause", null, String.class);
        assertThat(pause.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pause.getBody()).contains("\"running\":false");

        ResponseEntity<String> resume = admin.postForEntity(baseUrl + "/api/simulator/resume", null, String.class);
        assertThat(resume.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resume.getBody()).contains("\"running\":true");

        simulatorMock.verify(1, getRequestedFor(urlEqualTo("/admin/scheduler")));
        simulatorMock.verify(1, postRequestedFor(urlEqualTo("/admin/scheduler/pause")));
        simulatorMock.verify(1, postRequestedFor(urlEqualTo("/admin/scheduler/resume")));
    }
}
