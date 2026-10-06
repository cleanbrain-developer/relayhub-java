package me.cleanbrain.relayhub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A direct link or hard refresh on any client-side React Router route must forward to
 * {@code index.html}, not 404 — SpaController had no test coverage at all until this (self-review
 * finding, 2026-10-02's sequel, caught during the react-router-dom v7 upgrade's own verification):
 * Stage 3 of the integration-platform overhaul (2026-09-30) added the List -&gt; Detail routes
 * (`/sources/:key`, `/targets/:key`, `/subscriptions/:id`) to the frontend router but never updated
 * SpaController's explicit route list to match, so a direct link or hard refresh on any of those
 * detail pages had 404'd in production ever since — every verification of that stage only ever
 * navigated there via an in-app client-side {@code <Link>} click, which never hits the server for
 * that URL at all.
 *
 * <p>Relies on {@code src/test/resources/static/index.html}, a minimal test-only fixture — not
 * the real built frontend. The {@code test} Gradle task never runs {@code npm run build} (that's
 * the separate {@code frontend-test} CI job and the Docker image build), so this test originally
 * failed in CI despite passing locally: locally it happened to pass only because a real build had
 * already populated {@code src/main/resources/static/} from an earlier, unrelated local `npm run
 * build` run, which a fresh CI checkout never has. The fixture makes this test's result
 * independent of whether that local build artifact happens to exist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class SpaControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void everyClientSideRouteForwardsToIndexInsteadOf404ing() {
        String baseUrl = "http://localhost:" + port;
        for (String path : new String[]{
                "/", "/sources", "/targets", "/subscriptions", "/deliveries", "/settings", "/live", "/login",
                // The detail routes Stage 3 added -- the ones that were missing here.
                "/sources/some-key", "/targets/some-key", "/subscriptions/" + java.util.UUID.randomUUID(),
        }) {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + path, String.class);
            assertThat(response.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).as("GET %s should forward to the SPA shell", path).contains("<div id=\"root\">");
        }
    }
}
