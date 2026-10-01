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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code @Valid} request-body failure must return this API's own {@link
 * me.cleanbrain.relayhub.common.ApiError} shape, like every other error path, not Spring's default
 * {@code ProblemDetail} response — self-review finding, 2026-10-02 (see
 * GlobalExceptionHandler#handleValidation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = "relayhub.delivery-tasks")
@ActiveProfiles("test")
class ValidationErrorResponseTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blankRequiredFieldReturnsApiErrorShapeNotSpringDefault() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.withBasicAuth("admin", "admin").postForEntity(
                "http://localhost:" + port + "/api/sources",
                new HttpEntity<>("""
                        {"key":"","name":"","description":"x"}
                        """, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("path").asText()).isEqualTo("/api/sources");
        assertThat(body.get("message").asText()).contains("Validation failed").contains("key").contains("name");
    }
}
