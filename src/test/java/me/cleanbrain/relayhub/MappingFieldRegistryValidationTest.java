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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Field registry validation, warning only" (maintainer request 2026-09-30, follow-up to the
 * integration-platform overhaul): a Subscription's response now carries {@code mappingWarnings}
 * for any target field or source JSONPath its {@code targetPayloadTemplate} references that isn't
 * registered in the Spec 006 field registry. Never blocks create/update — see
 * MappingValidationService's own Javadoc for why (Spec 006 deliberately kept the registry a
 * documentation/autocomplete aid, not a hard constraint).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class MappingFieldRegistryValidationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void subscriptionResponseWarnsOnlyAboutUnregisteredFieldsAndJsonPaths() throws Exception {
        String baseUrl = "http://localhost:" + port;

        postJson(baseUrl + "/api/sources", """
                {"key":"warn-source","name":"Warn Source","description":"x"}
                """);
        postJson(baseUrl + "/api/sources/warn-source/events", """
                {"key":"created","name":"Created","description":"x","resourceType":"thing",
                 "operation":"CREATED","resourceIdPath":"$.id"}
                """);
        // Only "customerNo" is registered -- "unregisteredPath" below is deliberately not.
        postJson(baseUrl + "/api/sources/warn-source/events/created/fields", """
                {"key":"customerNo","jsonPath":"$.customerNo","dataType":"STRING","required":true,"sensitive":false}
                """);

        postJson(baseUrl + "/api/targets", """
                {"key":"warn-target","name":"Warn Target","description":"x","baseUrl":"http://localhost:1"}
                """);
        postJson(baseUrl + "/api/targets/warn-target/endpoints", """
                {"key":"webhook","name":"Webhook","description":"x","httpMethod":"POST","path":"/webhook"}
                """);
        // Only "dealerId" is registered -- "unregisteredField" below is deliberately not.
        postJson(baseUrl + "/api/targets/warn-target/endpoints/webhook/fields", """
                {"key":"dealerId","dataType":"STRING","required":true,"sensitive":false}
                """);

        ResponseEntity<String> created = postJson(baseUrl + "/api/subscriptions", """
                {
                  "sourceKey":"warn-source","sourceEventKey":"created","targetKey":"warn-target",
                  "targetEndpointKey":"webhook","name":"Warn Sub","description":"x",
                  "targetPayloadTemplate":"{\\"dealerId\\":\\"${$.customerNo}\\",\\"unregisteredField\\":\\"${$.unregisteredPath}\\"}"
                }
                """);
        JsonNode response = objectMapper.readTree(created.getBody());
        JsonNode warnings = response.get("mappingWarnings");
        assertThat(warnings).hasSize(2);
        String allWarnings = warnings.toString();
        assertThat(allWarnings).contains("unregisteredField").contains("unregisteredPath");
        assertThat(allWarnings).doesNotContain("\"dealerId\" is not registered").doesNotContain("customerNo\" is not registered");

        // GET (list and single) recompute the same warnings fresh, not just the create response.
        String id = response.get("id").asText();
        JsonNode fetched = objectMapper.readTree(getBody(baseUrl + "/api/subscriptions/" + id));
        assertThat(fetched.get("mappingWarnings")).hasSize(2);

        // Registering the missing field/JSONPath makes the warnings disappear on the next read --
        // computed fresh from current registry state, not cached at create time.
        postJson(baseUrl + "/api/targets/warn-target/endpoints/webhook/fields", """
                {"key":"unregisteredField","dataType":"STRING","required":false,"sensitive":false}
                """);
        postJson(baseUrl + "/api/sources/warn-source/events/created/fields", """
                {"key":"unregisteredPath","jsonPath":"$.unregisteredPath","dataType":"STRING","required":false,"sensitive":false}
                """);
        JsonNode afterRegistering = objectMapper.readTree(getBody(baseUrl + "/api/subscriptions/" + id));
        assertThat(afterRegistering.get("mappingWarnings")).isEmpty();
    }

    private ResponseEntity<String> postJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TestRestTemplate client = restTemplate.withBasicAuth("admin", "admin");
        ResponseEntity<String> response = client.postForEntity(url, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("POST %s failed: %s", url, response.getBody())
                .isTrue();
        return response;
    }

    private String getBody(String url) {
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }
}
