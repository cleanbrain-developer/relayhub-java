package me.cleanbrain.relayhub.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.ApiKeyAuth;
import me.cleanbrain.relayhub.common.AuthenticationType;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.deliverysettings.DeliverySettingsService;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.live.LiveActivityBroadcaster;
import me.cleanbrain.relayhub.live.LiveEvent;
import me.cleanbrain.relayhub.mapping.MappingService;
import me.cleanbrain.relayhub.subscription.Subscription;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import me.cleanbrain.relayhub.target.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Delivers a Canonical Event to one Subscription's Target over HTTP, with retry/backoff and a
 * dead-letter ({@link DeliveryState#DEAD}) terminal state. See specs/002-retry-dlq-replay/spec.md
 * and, for the Stage 2 non-blocking retry engine (maintainer request 2026-09-30),
 * db/migration/V12__delivery_retry_state.sql and {@link DeliveryState}.
 *
 * <p>Each individual attempt still runs synchronously (one blocking HTTP call, capped by the
 * effective {@code timeoutMs}), but the backoff <em>wait</em> between attempts does not — a failed
 * attempt persists {@link DeliveryState#RETRYING} + {@code nextAttemptAt} and returns immediately,
 * releasing the calling thread (and its DB connection) back to the pool. {@link DeliveryRetryScheduler}
 * picks the delivery back up once due. This also makes an in-progress retry schedule durable across
 * a crash — a {@code RETRYING} row survives a restart and resumes on its own, unlike the old
 * in-memory {@code Thread.sleep} loop, which lost the rest of its schedule if the process died
 * mid-retry (see ADR-0003 for why that gap existed at all).
 */
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final int MAX_RECORDED_BODY_LENGTH = 4000;

    // Defaults for the DeliveryPolicy fields Stage 1 added to Subscription but left unread
    // (nullable = "use the global default") — maxAttempts alone had a real global default
    // (DeliverySettingsService, DB-configurable) before Stage 2; these five didn't, so Stage 2 is
    // where they need actual values. Chosen to reproduce the old fixed {200, 400} backoff exactly
    // for the common maxAttempts=3 case: initialBackoffMs=200, multiplier=2.0 -> 200, 400, ... —
    // same numbers, now exponential instead of linear so they stay sane at higher attempt counts.
    private static final int DEFAULT_INITIAL_BACKOFF_MS = 200;
    private static final int DEFAULT_MAX_BACKOFF_MS = 30_000;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    // false, not true: keeps default timing exactly reproducible/deterministic (matches the old
    // behavior precisely) — jitter is an explicit per-Subscription opt-in via the DeliveryPolicy
    // fields Stage 1 already exposes, not a silently-applied default.
    private static final boolean DEFAULT_JITTER = false;
    private static final int DEFAULT_TIMEOUT_MS = 10_000;

    private final MappingService mappingService;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final LiveActivityBroadcaster liveActivityBroadcaster;
    private final DeliverySettingsService deliverySettingsService;

    // Forces HTTP/1.1: the JDK HttpClient's default HTTP/2 upgrade attempt causes
    // "EOF reached while reading" against plain HTTP/1.1 Target servers (observed against
    // WireMock in tests; a real Target is equally unlikely to speak h2c). Shared across attempts
    // for connection pooling/keep-alive — only the per-attempt read timeout varies (see
    // buildRestClient), and JdkClientHttpRequestFactory wrapping it is a cheap, connection-less
    // object, so a fresh one per attempt costs nothing but avoids a mutable shared read-timeout
    // field racing across concurrently in-flight attempts.
    private final HttpClient sharedHttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

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
                .targetId(subscription.getTargetEndpoint().getTarget().getId())
                .state(DeliveryState.PENDING)
                .attemptCount(0)
                .build());

        return runAttempt(delivery, subscription, sourcePayload, 1, DeliveryState.PROCESSING, true);
    }

    /**
     * Called by {@link DeliveryRetryScheduler} once a {@code RETRYING} delivery's {@code
     * nextAttemptAt} has elapsed. Re-checks the state under this method's own transaction before
     * attempting — the delivery may have been manually replayed (if it had reached DEAD some other
     * way) or otherwise moved on between the scheduler's batch read and this call; a no-longer-
     * RETRYING delivery is skipped rather than double-attempted.
     */
    @Transactional
    public void processDueRetry(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null || delivery.getState() != DeliveryState.RETRYING) {
            return;
        }
        Subscription subscription = subscriptionRepository.findWithDetailsById(delivery.getSubscriptionId()).orElse(null);
        Event event = eventRepository.findById(delivery.getEventId()).orElse(null);
        if (subscription == null || event == null) {
            // Subscription/Event was hard-deleted out from under a still-retrying Delivery — leave
            // it RETRYING rather than guessing at a terminal state; an operator can see it stuck
            // and hard-delete the Delivery itself, same as any other orphaned-FK situation in this
            // codebase (see the various hard-delete guards elsewhere).
            log.warn("Skipping retry for delivery {} — its subscription or event no longer exists", deliveryId);
            return;
        }
        JsonNode sourcePayload = parsePayload(event.getPayload());
        runAttempt(delivery, subscription, sourcePayload, delivery.getAttemptCount() + 1, DeliveryState.PROCESSING, true);
    }

    /**
     * Re-attempts a DEAD delivery. Two callers: the admin console's manual Replay button
     * (DeliveryController), and DlqAutoReplayScheduler's periodic sweep — both go through this
     * same method, so both get the same idempotency/state guard and the same "delivery"
     * live-activity broadcast.
     *
     * <p>Unlike before Stage 2, a failed replay does not necessarily land back on DEAD immediately
     * — it re-enters the normal retry loop (DEAD -&gt; REPLAYING -&gt; PROCESSING -&gt; RETRYING -&gt;
     * ... ) exactly as if this were any other attempt, so raising {@code maxAttempts} (globally or
     * on this Subscription) after a delivery went DEAD lets replay actually benefit from the extra
     * attempts instead of only ever getting one more try.
     *
     * <p>Deliberately does NOT re-broadcast "dlq" if the replay (or a subsequent scheduled retry
     * spawned from it) lands back on DEAD: the delivery was already DEAD (that's the precondition
     * below) and, in the common case (maxAttempts unchanged), immediately exhausts again — nothing
     * was newly added to the DLQ, so a second "dlq" pulse here would fly to the DLQ node without
     * the count actually changing. An earlier version broadcast it unconditionally on every renewed
     * failure, which is exactly what looked like the count "randomly" going up or down relative to
     * the missile animation (maintainer feedback 2026-09-12) — "dlq" now only ever fires from
     * deliver()'s/processDueRetry's retry-exhaustion path, the one true DEAD-count increment.
     */
    @Transactional
    public Delivery replay(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("Delivery not found: " + deliveryId));
        if (delivery.getState() != DeliveryState.DEAD) {
            throw new IllegalStateException("Delivery %s is not DEAD (state=%s); only a DEAD delivery can be replayed"
                    .formatted(deliveryId, delivery.getState()));
        }

        Subscription subscription = subscriptionRepository.findWithDetailsById(delivery.getSubscriptionId())
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + delivery.getSubscriptionId()));
        Event event = eventRepository.findById(delivery.getEventId())
                .orElseThrow(() -> new NotFoundException("Event not found: " + delivery.getEventId()));
        JsonNode sourcePayload = parsePayload(event.getPayload());

        Delivery result = runAttempt(delivery, subscription, sourcePayload, delivery.getAttemptCount() + 1, DeliveryState.REPLAYING, false);
        meterRegistry.counter("relayhub.delivery.replay", "outcome", result.getState().name().toLowerCase()).increment();
        return result;
    }

    /**
     * Performs one HTTP attempt and advances the Delivery's state machine to whatever comes next
     * (SUCCEEDED, RETRYING with a freshly computed {@code nextAttemptAt}, or DEAD). Shared by
     * {@link #deliver}, {@link #processDueRetry}, and {@link #replay} — the only differences
     * between those three callers are which transient in-flight state to show while the attempt is
     * running (PROCESSING vs REPLAYING) and whether landing on DEAD should broadcast a "dlq"
     * live-activity pulse (see {@link #replay}'s Javadoc for why replay never does).
     */
    private Delivery runAttempt(Delivery delivery, Subscription subscription, JsonNode sourcePayload,
                                 int attemptNumber, DeliveryState inFlightState, boolean broadcastOnDead) {
        delivery.setState(inFlightState);
        deliveryRepository.saveAndFlush(delivery);

        EffectiveDeliveryPolicy policy = resolvePolicy(subscription);
        boolean success = attemptOnce(delivery, subscription, sourcePayload, attemptNumber,
                inFlightState == DeliveryState.REPLAYING, policy.timeoutMs());

        if (success) {
            delivery.setState(DeliveryState.SUCCEEDED);
            delivery.setNextAttemptAt(null);
            meterRegistry.counter("relayhub.delivery.terminal", "state", "succeeded").increment();
            return deliveryRepository.save(delivery);
        }

        if (attemptNumber < policy.maxAttempts()) {
            delivery.setState(DeliveryState.RETRYING);
            delivery.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(attemptNumber, policy)));
            return deliveryRepository.save(delivery);
        }

        delivery.setState(DeliveryState.DEAD);
        delivery.setNextAttemptAt(null);
        meterRegistry.counter("relayhub.delivery.terminal", "state", "dead").increment();
        if (broadcastOnDead) {
            broadcastDlq(subscription);
        }
        return deliveryRepository.save(delivery);
    }

    private void broadcastDlq(Subscription subscription) {
        liveActivityBroadcaster.broadcast(LiveEvent.dlq(
                subscription.getSourceEvent().getSource().getKey(), subscription.getSourceEvent().getKey(),
                subscription.getTargetEndpoint().getTarget().getKey()));
    }

    private boolean attemptOnce(Delivery delivery, Subscription subscription, JsonNode sourcePayload,
                                 int attemptNumber, boolean isReplay, int timeoutMs) {
        JsonNode targetPayload = mappingService.map(sourcePayload, subscription.getTargetPayloadTemplate());
        String url = subscription.getTargetEndpoint().getTarget().getBaseUrl() + subscription.getTargetEndpoint().getPath();
        String requestBody = targetPayload.toString();

        DeliveryAttempt.DeliveryAttemptBuilder attempt = DeliveryAttempt.builder()
                .deliveryId(delivery.getId())
                .eventId(delivery.getEventId())
                .subscriptionId(delivery.getSubscriptionId())
                .targetId(delivery.getTargetId())
                .attemptNumber(attemptNumber)
                .requestMethod(subscription.getTargetEndpoint().getHttpMethod().name())
                .requestUrl(url)
                .requestBody(truncate(requestBody));

        boolean success;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            RestClient.RequestBodySpec requestSpec = buildRestClient(timeoutMs)
                    .method(subscription.getTargetEndpoint().getHttpMethod().toSpring())
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);
            requestSpec = applyApiKeyHeader(requestSpec, subscription.getTargetEndpoint().getTarget());

            String responseBody = requestSpec
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
        sample.stop(meterRegistry.timer("relayhub.delivery.attempt.duration", "status", success ? "success" : "failed"));
        meterRegistry.counter("relayhub.delivery.attempts", "status", success ? "success" : "failed").increment();

        // Saved before broadcasting (not after) so the attempt already has its real id — the Live
        // page's recent-activity feed carries that id so a click there can look up this exact
        // attempt's full request/response via GET /api/delivery-attempts/{id}, the same detail
        // view the Deliveries page uses.
        DeliveryAttempt savedAttempt = deliveryAttemptRepository.save(attempt.build());
        liveActivityBroadcaster.broadcast(LiveEvent.delivery(
                subscription.getSourceEvent().getSource().getKey(), subscription.getSourceEvent().getKey(),
                subscription.getTargetEndpoint().getTarget().getKey(), success, isReplay, savedAttempt.getId()));

        delivery.setAttemptCount(attemptNumber);
        return success;
    }

    /** {@code timeoutMs} is the only DeliveryPolicy field applied per-request here — a fresh,
     *  connection-less {@link JdkClientHttpRequestFactory} wrapping the one shared, connection-
     *  pooling {@link HttpClient}, so varying the read timeout per attempt never risks racing a
     *  mutable field shared across concurrently in-flight attempts. */
    private RestClient buildRestClient(int timeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(sharedHttpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * A Target with {@code authenticationType=API_KEY} gets {@link ApiKeyAuth#HEADER_NAME}
     * attached on every outbound delivery attempt (maintainer request 2026-09-30, "API_KEY 인증
     * 실제 적용" — until now {@code authenticationConfig} was stored but never actually attached to
     * an outbound request). {@code NONE} (the default, and every demo Target today) is unaffected.
     */
    private RestClient.RequestBodySpec applyApiKeyHeader(RestClient.RequestBodySpec requestSpec, Target target) {
        if (target.getAuthenticationType() != AuthenticationType.API_KEY) {
            return requestSpec;
        }
        String apiKey = target.getAuthenticationConfig();
        if (apiKey == null || apiKey.isBlank()) {
            return requestSpec;
        }
        return requestSpec.header(ApiKeyAuth.HEADER_NAME, apiKey);
    }

    private record EffectiveDeliveryPolicy(
            int maxAttempts, int initialBackoffMs, int maxBackoffMs, double backoffMultiplier, boolean jitter, int timeoutMs) {
    }

    /** Resolves Stage 1's nullable per-Subscription DeliveryPolicy overrides against their
     *  defaults — null means "use the default" for every field. maxAttempts alone falls back to
     *  the DB-configurable global default (DeliverySettingsService); the rest fall back to the
     *  fixed constants above, since no per-deployment global setting exists for them (see this
     *  class's own Javadoc on those constants). */
    private EffectiveDeliveryPolicy resolvePolicy(Subscription subscription) {
        return new EffectiveDeliveryPolicy(
                subscription.getMaxAttempts() != null ? subscription.getMaxAttempts() : deliverySettingsService.getMaxAttempts(),
                subscription.getInitialBackoffMs() != null ? subscription.getInitialBackoffMs() : DEFAULT_INITIAL_BACKOFF_MS,
                subscription.getMaxBackoffMs() != null ? subscription.getMaxBackoffMs() : DEFAULT_MAX_BACKOFF_MS,
                subscription.getBackoffMultiplier() != null ? subscription.getBackoffMultiplier() : DEFAULT_BACKOFF_MULTIPLIER,
                subscription.getJitter() != null ? subscription.getJitter() : DEFAULT_JITTER,
                subscription.getTimeoutMs() != null ? subscription.getTimeoutMs() : DEFAULT_TIMEOUT_MS);
    }

    /** Backoff before the attempt after {@code attemptNumber}: {@code initialBackoffMs *
     *  backoffMultiplier^(attemptNumber-1)}, capped at {@code maxBackoffMs}. With jitter, applies
     *  "equal jitter" (half fixed + half random) rather than "full jitter" (0..computed) — a
     *  predictable floor still bounds worst-case DLQ latency, while the random half still breaks up
     *  a thundering herd of deliveries retrying a just-recovered Target in lockstep. */
    private long computeBackoffMillis(int attemptNumber, EffectiveDeliveryPolicy policy) {
        double raw = policy.initialBackoffMs() * Math.pow(policy.backoffMultiplier(), attemptNumber - 1);
        long capped = (long) Math.min(raw, policy.maxBackoffMs());
        if (!policy.jitter()) {
            return capped;
        }
        long half = capped / 2;
        return half + ThreadLocalRandom.current().nextLong(half + 1);
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
