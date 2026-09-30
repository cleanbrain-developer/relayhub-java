package me.cleanbrain.relayhub.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.outbox.OutboxPublisher;
import me.cleanbrain.relayhub.subscription.Subscription;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@link DeliveryTaskMessage}s published by {@link OutboxPublisher} and calls
 * {@link DeliveryService#deliver(Event, Subscription, JsonNode)}, which performs the first attempt
 * synchronously here and, on failure, hands any further retries off to
 * {@link DeliveryRetryScheduler} rather than blocking this consumer thread through the whole
 * backoff schedule (see DeliveryService's own Javadoc — Stage 2 of the integration-platform
 * overhaul, maintainer request 2026-09-30). See specs/003-kafka-outbox/spec.md.
 */
@Component
@RequiredArgsConstructor
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    // @Transactional here (not just on DeliveryService.deliver()) matters: it keeps one Hibernate
    // session open across the Subscription/Event loads below AND the deliver() call, so
    // subscription.getTargetEndpoint().getTarget() (lazy associations) can still initialize.
    // Without it, findById returns a detached entity in its own short transaction, and deliver()'s
    // separate transaction hits a LazyInitializationException — observed live in this test.
    @KafkaListener(topics = OutboxPublisher.TOPIC, groupId = "relayhub-delivery-worker")
    @Transactional
    public void onDeliveryTask(DeliveryTaskMessage message) {
        Event event = eventRepository.findById(message.eventId())
                .orElseThrow(() -> new NotFoundException("Event not found: " + message.eventId()));
        Subscription subscription = subscriptionRepository.findById(message.subscriptionId())
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + message.subscriptionId()));

        JsonNode payload = parsePayload(event.getPayload());
        deliveryService.deliver(event, subscription, payload);
        log.info("Processed delivery task {} for event {} / subscription {}",
                message.outboxEventId(), message.eventId(), message.subscriptionId());
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Stored Event payload is not valid JSON", e);
        }
    }
}
