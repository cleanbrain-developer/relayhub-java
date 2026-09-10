package me.cleanbrain.relayhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Verifies Spec 005's auth boundary directly (specs/005-admin-console/spec.md "Acceptance"):
 * writes under /api/** require the admin credential, reads stay public, and delete is a soft
 * delete (status -&gt; INACTIVE, row not removed). Same @SpringBootTest/@EmbeddedKafka
 * configuration as IngressVerticalSliceTest so Spring's test context caching reuses that
 * context — see RelayHubApplicationTests's comment for why a differently-configured context
 * sharing the "test" profile's H2 database name breaks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class AdminConsoleApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesRequireAuth_readsArePublic_deleteIsSoftDelete() throws Exception {
        String baseUrl = "http://localhost:" + port;
        String createBody = """
                {"key":"admin-console-source","name":"Admin Console Source","description":"Spec 005 test"}
                """;

        // No credentials -> 401.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> unauthenticated = restTemplate.postForEntity(
                baseUrl + "/api/sources", new HttpEntity<>(createBody, headers), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Wrong credentials -> 401.
        ResponseEntity<String> wrongAuth = restTemplate.withBasicAuth("admin", "not-the-password")
                .postForEntity(baseUrl + "/api/sources", new HttpEntity<>(createBody, headers), String.class);
        assertThat(wrongAuth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Correct credentials -> 201.
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");
        ResponseEntity<String> created = admin.postForEntity(
                baseUrl + "/api/sources", new HttpEntity<>(createBody, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET (list and get-by-key) is public — no credentials needed.
        ResponseEntity<String> list = restTemplate.getForEntity(baseUrl + "/api/sources", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("admin-console-source");

        ResponseEntity<String> get = restTemplate.getForEntity(baseUrl + "/api/sources/admin-console-source", String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(get.getBody()).get("status").asText()).isEqualTo("ACTIVE");

        // PUT without credentials -> 401; PUT with credentials -> 200, fields updated.
        String updateBody = """
                {"name":"Renamed Source","description":"Updated by Spec 005 test"}
                """;
        ResponseEntity<String> unauthenticatedUpdate = restTemplate.exchange(
                baseUrl + "/api/sources/admin-console-source", HttpMethod.PUT,
                new HttpEntity<>(updateBody, headers), String.class);
        assertThat(unauthenticatedUpdate.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> updated = admin.exchange(
                baseUrl + "/api/sources/admin-console-source", HttpMethod.PUT,
                new HttpEntity<>(updateBody, headers), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedBody = objectMapper.readTree(updated.getBody());
        assertThat(updatedBody.get("name").asText()).isEqualTo("Renamed Source");
        assertThat(updatedBody.get("description").asText()).isEqualTo("Updated by Spec 005 test");

        // DELETE without credentials -> 401; with credentials -> 204, soft delete (row stays,
        // status flips to INACTIVE — not removed, GET by key still resolves it).
        ResponseEntity<String> unauthenticatedDelete = restTemplate.exchange(
                baseUrl + "/api/sources/admin-console-source", HttpMethod.DELETE, null, String.class);
        assertThat(unauthenticatedDelete.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Void> deleted = admin.exchange(
                baseUrl + "/api/sources/admin-console-source", HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getAfterDelete = restTemplate.getForEntity(
                baseUrl + "/api/sources/admin-console-source", String.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(getAfterDelete.getBody()).get("status").asText()).isEqualTo("INACTIVE");

        // /ingress/v1/** stays public regardless of the /api/** auth boundary — a Source-facing
        // request without credentials against a nonexistent path should 404, not 401.
        ResponseEntity<String> ingressProbe = restTemplate.postForEntity(
                baseUrl + "/ingress/v1/nonexistent-source/nonexistent-event",
                new HttpEntity<>("{}", headers), String.class);
        assertThat(ingressProbe.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
