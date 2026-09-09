package me.cleanbrain.relayhub.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.mapping.MappingService;
import me.cleanbrain.relayhub.subscription.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;

/**
 * Delivers a Canonical Event to one Subscription's Target over HTTP.
 * Phase 1 calls the Target inline (in-process), not via Kafka/Outbox — see
 * docs/architecture/system-design.md ("Target reliability structure") for the deferred
 * Reliability-phase pipeline this will grow into.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final int MAX_RECORDED_BODY_LENGTH = 4000;

    private final MappingService mappingService;
    private final DeliveryAttemptRepository deliveryAttemptRepository;

    // Forces HTTP/1.1: the JDK HttpClient's default HTTP/2 upgrade attempt causes
    // "EOF reached while reading" against plain HTTP/1.1 Target servers (observed against
    // WireMock in tests; a real Target is equally unlikely to speak h2c).
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
            .build();

    @Transactional
    public DeliveryAttempt deliver(Event event, Subscription subscription, JsonNode sourcePayload) {
        JsonNode targetPayload = mappingService.map(sourcePayload, subscription.getTargetPayloadTemplate());
        String url = subscription.getTarget().getBaseUrl() + subscription.getTargetPath();

        DeliveryAttempt.DeliveryAttemptBuilder attempt = DeliveryAttempt.builder()
                .eventId(event.getId())
                .subscriptionId(subscription.getId())
                .targetId(subscription.getTarget().getId())
                .attemptNumber(1);

        try {
            String responseBody = restClient.method(subscription.getTargetMethod().toSpring())
                    .uri(url)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(targetPayload)
                    .retrieve()
                    .body(String.class);

            attempt.status(DeliveryStatus.SUCCESS)
                    .httpStatus(200)
                    .responseBody(truncate(responseBody));
        } catch (RestClientResponseException e) {
            log.warn("Target delivery failed for subscription {}: HTTP {}", subscription.getId(), e.getStatusCode());
            attempt.status(DeliveryStatus.FAILED)
                    .httpStatus(e.getStatusCode().value())
                    .responseBody(truncate(e.getResponseBodyAsString()))
                    .errorMessage(e.getMessage());
        } catch (Exception e) {
            log.warn("Target delivery failed for subscription {}: {}", subscription.getId(), e.getMessage());
            attempt.status(DeliveryStatus.FAILED)
                    .errorMessage(e.getMessage());
        }

        return deliveryAttemptRepository.save(attempt.build());
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_RECORDED_BODY_LENGTH ? value.substring(0, MAX_RECORDED_BODY_LENGTH) : value;
    }
}
