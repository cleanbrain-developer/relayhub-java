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
 * Spec 006 (specs/006-field-registry/spec.md): SourceField/TargetField CRUD, following the same
 * list/create/edit/soft-delete(+admin hard-delete) contract as every other admin-console entity
 * (see AdminConsoleApiTest), plus the FK guard that blocks hard-deleting a SourceEvent/Target
 * while a field is still registered on it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class FieldRegistryApiTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sourceFieldAndTargetFieldCrud_andHardDeleteGuard() throws Exception {
        String baseUrl = "http://localhost:" + port;
        TestRestTemplate admin = restTemplate.withBasicAuth("admin", "admin");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        admin.postForEntity(baseUrl + "/api/sources", new HttpEntity<>("""
                {"key":"field-test-source","name":"Field Test Source","description":"x"}
                """, headers), String.class);
        admin.postForEntity(baseUrl + "/api/sources/field-test-source/events", new HttpEntity<>("""
                {"key":"created","name":"Created","description":"x","resourceType":"thing","operation":"CREATED","resourceIdPath":"$.id"}
                """, headers), String.class);
        admin.postForEntity(baseUrl + "/api/targets", new HttpEntity<>("""
                {"key":"field-test-target","name":"Field Test Target","description":"x","baseUrl":"http://localhost:1"}
                """, headers), String.class);

        // Create requires auth.
        String sourceFieldBody = """
                {"key":"customerNo","jsonPath":"$.customerNo","dataType":"STRING","description":"Customer number",
                 "exampleValue":"C-123","required":true,"sensitive":false}
                """;
        ResponseEntity<String> unauthenticated = restTemplate.postForEntity(
                baseUrl + "/api/sources/field-test-source/events/created/fields",
                new HttpEntity<>(sourceFieldBody, headers), String.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> sourceFieldCreated = admin.postForEntity(
                baseUrl + "/api/sources/field-test-source/events/created/fields",
                new HttpEntity<>(sourceFieldBody, headers), String.class);
        assertThat(sourceFieldCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode sourceFieldJson = objectMapper.readTree(sourceFieldCreated.getBody());
        assertThat(sourceFieldJson.get("sourceKey").asText()).isEqualTo("field-test-source");
        assertThat(sourceFieldJson.get("sourceEventKey").asText()).isEqualTo("created");
        assertThat(sourceFieldJson.get("jsonPath").asText()).isEqualTo("$.customerNo");
        assertThat(sourceFieldJson.get("status").asText()).isEqualTo("ACTIVE");

        // Duplicate key on the same event -> 400.
        ResponseEntity<String> duplicate = admin.postForEntity(
                baseUrl + "/api/sources/field-test-source/events/created/fields",
                new HttpEntity<>(sourceFieldBody, headers), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String targetFieldBody = """
                {"key":"dealerId","dataType":"STRING","description":"Dealer id","exampleValue":"D-1",
                 "required":true,"sensitive":false}
                """;
        ResponseEntity<String> targetFieldCreated = admin.postForEntity(
                baseUrl + "/api/targets/field-test-target/fields", new HttpEntity<>(targetFieldBody, headers), String.class);
        assertThat(targetFieldCreated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(objectMapper.readTree(targetFieldCreated.getBody()).get("targetKey").asText()).isEqualTo("field-test-target");

        // List/get are public.
        ResponseEntity<String> sourceFieldList = restTemplate.getForEntity(
                baseUrl + "/api/sources/field-test-source/events/created/fields", String.class);
        assertThat(sourceFieldList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sourceFieldList.getBody()).contains("customerNo");

        ResponseEntity<String> targetFieldList = restTemplate.getForEntity(
                baseUrl + "/api/targets/field-test-target/fields", String.class);
        assertThat(targetFieldList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(targetFieldList.getBody()).contains("dealerId");

        // Update.
        String sourceFieldUpdate = """
                {"jsonPath":"$.customer.number","dataType":"STRING","description":"Renamed","exampleValue":"C-999",
                 "required":false,"sensitive":true}
                """;
        ResponseEntity<String> sourceFieldUpdated = admin.exchange(
                baseUrl + "/api/sources/field-test-source/events/created/fields/customerNo", HttpMethod.PUT,
                new HttpEntity<>(sourceFieldUpdate, headers), String.class);
        assertThat(sourceFieldUpdated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updatedJson = objectMapper.readTree(sourceFieldUpdated.getBody());
        assertThat(updatedJson.get("jsonPath").asText()).isEqualTo("$.customer.number");
        assertThat(updatedJson.get("sensitive").asBoolean()).isTrue();

        // Hard-deleting the owning SourceEvent/Target is blocked while a field is still registered.
        ResponseEntity<String> eventBlocked = admin.exchange(
                baseUrl + "/api/sources/field-test-source/events/created?hard=true", HttpMethod.DELETE, null, String.class);
        assertThat(eventBlocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> targetBlocked = admin.exchange(
                baseUrl + "/api/targets/field-test-target?hard=true", HttpMethod.DELETE, null, String.class);
        assertThat(targetBlocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Hard-delete the fields themselves -> 204, then GET 404s.
        ResponseEntity<Void> sourceFieldDeleted = admin.exchange(
                baseUrl + "/api/sources/field-test-source/events/created/fields/customerNo?hard=true",
                HttpMethod.DELETE, null, Void.class);
        assertThat(sourceFieldDeleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(
                        baseUrl + "/api/sources/field-test-source/events/created/fields/customerNo", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> targetFieldDeleted = admin.exchange(
                baseUrl + "/api/targets/field-test-target/fields/dealerId?hard=true", HttpMethod.DELETE, null, Void.class);
        assertThat(targetFieldDeleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity(baseUrl + "/api/targets/field-test-target/fields/dealerId", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Now the SourceEvent/Target hard-delete is unblocked.
        assertThat(admin.exchange(baseUrl + "/api/sources/field-test-source/events/created?hard=true",
                HttpMethod.DELETE, null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(admin.exchange(baseUrl + "/api/targets/field-test-target?hard=true",
                HttpMethod.DELETE, null, Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
