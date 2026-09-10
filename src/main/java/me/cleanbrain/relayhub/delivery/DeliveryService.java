package me.cleanbrain.relayhub.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.mapping.MappingService;
import me.cleanbrain.relayhub.subscription.Subscription;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;

/**
 * Delivers a Canonical Event to one Subscription's Target over HTTP, with retry/backoff and a
 * dead-letter ({@link DeliveryState#DEAD}) terminal state. See specs/002-retry-dlq-replay/spec.md.
 *
 * <p>Retries run in-process, synchronously within the caller's transaction (typically the ingress
 * request) — there is no scheduler or message broker yet. A {@code PENDING} delivery is therefore
 * not durable across a crash mid-retry; only {@code SUCCEEDED}/{@code DEAD} are safe terminal
 * states. Closing this gap with a Transactional Outbox + Kafka pipeline is Spec 003's job, not
 * this one's — see docs/decisions/ADR-0003-incremental-reliability-phase.md.
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final int MAX_RECORDED_BODY_LENGTH = 4000;

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MILLIS = {200, 400};

    private final MappingService mappingService;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    // Forces HTTP/1.1: the JDK HttpClient's default HTTP/2 upgrade attempt causes
    // "EOF reached while reading" against plain HTTP/1.1 Target servers (observed against
    // WireMock in tests; a real Target is equally unlikely to speak h2c).
    private final RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
            .build();

    @Transactional
    public Delivery deliver(Event event, Subscription subscription, JsonNode sourcePayload) {
        // Idempotency at the delivery-task level: a Kafka-redelivered DeliveryTaskMessage (e.g.
        // after DeliveryWorker crashes before its offset commits) must not create a second
        // Delivery for the same (Event, Subscription) pair and re-run the whole retry loop. This
        // is separate from Spec 002's ingress-level idempotency-key dedup, which only prevents a
        // duplicate *Event* — it says nothing about a single Event's own delivery tasks being
        // reprocessed. See specs/003-kafka-outbox/spec.md ("Deliberately out of scope") and ADR-0004.
        //
        // This SELECT-then-INSERT covers the common case (sequential redelivery after a worker
        // restart) exactly. It does not defend against two truly concurrent transactions racing
        // on the same pair (only possible during a brief consumer-group rebalance, since a single
        // partition is otherwise processed by one consumer at a time): the DB's unique constraint
        // on (event_id, subscription_id) still catches that, but deliberately as an uncaught
        // DataIntegrityViolationException here, not a recovered one. Catching it and querying
        // again in the same transaction was tried and rejected — Postgres marks a transaction
        // unusable for further statements after a constraint violation, so that fallback query
        // would itself fail. Letting it propagate out of this @KafkaListener-invoked method lets
        // Spring Kafka's normal redelivery retry the message, and the retry's SELECT then finds
        // the row the other transaction committed.
        var existing = deliveryRepository.findByEventIdAndSubscriptionId(event.getId(), subscription.getId());
        if (existing.isPresent()) {
            log.info("Delivery already exists for event {} / subscription {} (state={}) — skipping duplicate delivery task",
                    event.getId(), subscription.getId(), existing.get().getState());
            return existing.get();
        }

        // saveAndFlush (not save): forces the INSERT to execute now, in its own statement, so
        // @CreationTimestamp actually populates createdAt. Observed live against real Postgres:
        // deferring the flush to transaction commit let this INSERT and the later state-update
        // UPDATE coalesce into a single statement, silently leaving created_at null while
        // updated_at (GenerationTiming.ALWAYS) still populated correctly.
        Delivery delivery = deliveryRepository.saveAndFlush(Delivery.builder()
                .eventId(event.getId())
                .subscriptionId(subscription.getId())
                .targetId(subscription.getTarget().getId())
                .state(DeliveryState.PENDING)
                .attemptCount(0)
                .build());

        for (int attemptNumber = 1; attemptNumber <= MAX_ATTEMPTS; attemptNumber++) {
            boolean success = attemptOnce(delivery, subscription, sourcePayload, attemptNumber);
            if (success) {
                delivery.setState(DeliveryState.SUCCEEDED);
                return deliveryRepository.save(delivery);
            }
            if (attemptNumber < MAX_ATTEMPTS) {
                sleepBackoff(attemptNumber);
            }
        }

        delivery.setState(DeliveryState.DEAD);
        return deliveryRepository.save(delivery);
    }

    /** Re-attempts a DEAD delivery once. Operator-triggered only — see spec.md ("Deliberately out of scope"). */
    @Transactional
    public Delivery replay(java.util.UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Delivery not found: " + deliveryId));
        if (delivery.getState() != DeliveryState.DEAD) {
            throw new IllegalStateException("Delivery %s is not DEAD (state=%s); only a DEAD delivery can be replayed"
                    .formatted(deliveryId, delivery.getState()));
        }

        Subscription subscription = subscriptionRepository.findById(delivery.getSubscriptionId())
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + delivery.getSubscriptionId()));
        Event event = eventRepository.findById(delivery.getEventId())
                .orElseThrow(() -> new NotFoundException("Event not found: " + delivery.getEventId()));
        JsonNode sourcePayload = parsePayload(event.getPayload());

        boolean success = attemptOnce(delivery, subscription, sourcePayload, delivery.getAttemptCount() + 1);
        delivery.setState(success ? DeliveryState.SUCCEEDED : DeliveryState.DEAD);
        return deliveryRepository.save(delivery);
    }

    private boolean attemptOnce(Delivery delivery, Subscription subscription, JsonNode sourcePayload, int attemptNumber) {
        JsonNode targetPayload = mappingService.map(sourcePayload, subscription.getTargetPayloadTemplate());
        String url = subscription.getTarget().getBaseUrl() + subscription.getTargetPath();

        DeliveryAttempt.DeliveryAttemptBuilder attempt = DeliveryAttempt.builder()
                .deliveryId(delivery.getId())
                .eventId(delivery.getEventId())
                .subscriptionId(delivery.getSubscriptionId())
                .targetId(delivery.getTargetId())
                .attemptNumber(attemptNumber);

        boolean success;
        try {
            String responseBody = restClient.method(subscription.getTargetMethod().toSpring())
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(targetPayload)
                    .retrieve()
                    .body(String.class);

            attempt.status(DeliveryStatus.SUCCESS)
                    .httpStatus(200)
                    .responseBody(truncate(responseBody));
            success = true;
        } catch (RestClientResponseException e) {
            log.warn("Target delivery attempt {} failed for subscription {}: HTTP {}", attemptNumber, subscription.getId(), e.getStatusCode());
            attempt.status(DeliveryStatus.FAILED)
                    .httpStatus(e.getStatusCode().value())
                    .responseBody(truncate(e.getResponseBodyAsString()))
                    .errorMessage(e.getMessage());
            success = false;
        } catch (Exception e) {
            log.warn("Target delivery attempt {} failed for subscription {}: {}", attemptNumber, subscription.getId(), e.getMessage());
            attempt.status(DeliveryStatus.FAILED)
                    .errorMessage(e.getMessage());
            success = false;
        }

        deliveryAttemptRepository.save(attempt.build());
        delivery.setAttemptCount(attemptNumber);
        return success;
    }

    private void sleepBackoff(int attemptNumber) {
        try {
            Thread.sleep(BACKOFF_MILLIS[attemptNumber - 1]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Stored Event payload is not valid JSON", e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_RECORDED_BODY_LENGTH ? value.substring(0, MAX_RECORDED_BODY_LENGTH) : value;
    }
}
