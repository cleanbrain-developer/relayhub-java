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
 * TargetEndpoint CRUD (domain-model overhaul, maintainer request 2026-09-30) — same list/create/
 * edit/soft-delete(+admin hard-delete) contract as every other admin-console entity (see
 * AdminConsoleApiTest), nested under a Target the same way SourceEventController is nested under a
 * Source. FieldRegistryApiTest already exercises one TargetEndpoint indirectly as a prerequisite
 * for TargetField; this covers the endpoint resource itself directly (key uniqueness, update,
 * duplicate-key rejection, hard-delete guard while a TargetField still references it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class TargetEndpointApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void targetEndpointCrud_duplicateKeyRejected_andHardDeleteGuard() throws Exception {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        admin.postForEntity(baseUrl + "/api/targets", new HttpEntity<>("""
                {"key":"endpoint-test-target","name":"Endpoint Test Target","description":"x","baseUrl":"http://localhost:1"}
                """, headers), String.class);

        String endpointBody = """
                {"key":"create-customer","name":"Create Customer","description":"x","httpMethod":"POST","path":"/customers"}
                """;

        // Create requires auth.
        ResponseEntity<String> unauthenticated = restTemplate.postForEntity(
                baseUrl + "/api/targets/endpoint-test-target/endpoints",
                new HttpEntity<>(endpointBody, headers), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> created = admin.postForEntity(
                baseUrl + "/api/targets/endpoint-test-target/endpoints",
                new HttpEntity<>(endpointBody, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode createdJson = objectMapper.readTree(created.getBody());
        assertThat(createdJson.get("targetKey").asText()).isEqualTo("endpoint-test-target");
        assertThat(createdJson.get("key").asText()).isEqualTo("create-customer");
        assertThat(createdJson.get("httpMethod").asText()).isEqualTo("POST");
        assertThat(createdJson.get("path").asText()).isEqualTo("/customers");
        assertThat(createdJson.get("status").asText()).isEqualTo("ACTIVE");

        // Duplicate key on the same Target -> 400.
        ResponseEntity<String> duplicate = admin.postForEntity(
                baseUrl + "/api/targets/endpoint-test-target/endpoints",
                new HttpEntity<>(endpointBody, headers), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // List/get are public.
        ResponseEntity<String> list = restTemplate.getForEntity(
                baseUrl + "/api/targets/endpoint-test-target/endpoints", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("create-customer");

        // Update.
        String updateBody = """
                {"name":"Create Customer (renamed)","description":"y","httpMethod":"PUT","path":"/customers/{id}","timeoutOverrideMs":5000}
                """;
        ResponseEntity<String> updated = admin.exchange(
                baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer", HttpMethod.PUT,
                new HttpEntity<>(updateBody, headers), String.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedJson = objectMapper.readTree(updated.getBody());
        assertThat(updatedJson.get("name").asText()).isEqualTo("Create Customer (renamed)");
        assertThat(updatedJson.get("httpMethod").asText()).isEqualTo("PUT");
        assertThat(updatedJson.get("path").asText()).isEqualTo("/customers/{id}");
        assertThat(updatedJson.get("timeoutOverrideMs").asInt()).isEqualTo(5000);

        // Register a TargetField on it, then hard-delete is blocked while the field exists.
        admin.postForEntity(baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer/fields",
                new HttpEntity<>("""
                        {"key":"customerId","dataType":"STRING","description":"x","exampleValue":"C-1","required":true,"sensitive":false}
                        """, headers), String.class);

        ResponseEntity<String> hardDeleteBlocked = admin.exchange(
                baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer?hard=true",
                HttpMethod.DELETE, null, String.class);
        assertThat(hardDeleteBlocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Deactivate (soft delete) works regardless.
        ResponseEntity<Void> deactivated = admin.exchange(
                baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer", HttpMethod.DELETE, null, Void.class);
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> afterDeactivate = restTemplate.getForEntity(
                baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer", String.class);
        assertThat(objectMapper.readTree(afterDeactivate.getBody()).get("status").asText()).isEqualTo("INACTIVE");

        // Hard-delete the field, then the endpoint is unblocked.
        assertThat(admin.exchange(
                        baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer/fields/customerId?hard=true",
                        HttpMethod.DELETE, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(admin.exchange(
                        baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer?hard=true",
                        HttpMethod.DELETE, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(
                        baseUrl + "/api/targets/endpoint-test-target/endpoints/create-customer", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
