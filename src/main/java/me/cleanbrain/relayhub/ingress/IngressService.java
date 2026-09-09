package me.cleanbrain.relayhub.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.HttpVerb;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.delivery.DeliveryService;
import me.cleanbrain.relayhub.event.Event;
import me.cleanbrain.relayhub.event.EventRepository;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.sourceevent.SourceEventRepository;
import me.cleanbrain.relayhub.subscription.Subscription;
import me.cleanbrain.relayhub.subscription.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * Orchestrates the Phase 1 vertical slice: identify the Source Event from the Ingress URL,
 * validate and extract the Source payload, persist a Canonical Event, then deliver it to
 * every active Subscription. See docs/architecture/system-design.md ("Core data flow").
 */
@Service
@RequiredArgsConstructor
public class IngressService {

    private static final Logger log = LoggerFactory.getLogger(IngressService.class);

    private static final Configuration JSONPATH_CONFIGURATION = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

    private final SourceEventRepository sourceEventRepository;
    private final EventRepository eventRepository;
    private final SubscriptionService subscriptionService;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public IngressResult handle(String ingressPath, HttpMethod method, String rawBody, HttpServletRequest request) {
        SourceEvent sourceEvent = sourceEventRepository.findByIngressPathAndIngressMethod(ingressPath, HttpVerb.from(method))
                .orElseThrow(() -> new NotFoundException("No Source Event registered for %s %s".formatted(method, ingressPath)));

        JsonNode payload = parsePayload(rawBody);
        validateSchema(sourceEvent, payload);

        DocumentContext context = JsonPath.using(JSONPATH_CONFIGURATION).parse(payload);
        String resourceId = extractRequired(context, sourceEvent.getResourceIdPath(), "resourceIdPath");
        Instant occurredAt = extractOccurredAt(context, sourceEvent.getOccurredAtPath());
        String idempotencyKey = extractIdempotencyKey(context, sourceEvent, request);

        if (idempotencyKey != null) {
            var existing = eventRepository.findBySourceEventIdAndIdempotencyKey(sourceEvent.getId(), idempotencyKey);
            if (existing.isPresent()) {
                log.info("Duplicate idempotency key {} for Source Event {} — returning existing Event, no new deliveries",
                        idempotencyKey, sourceEvent.getId());
                return new IngressResult(existing.get(), 0, true);
            }
        }

        Event event = Event.builder()
                .sourceId(sourceEvent.getSource().getId())
                .sourceEventId(sourceEvent.getId())
                .resourceType(sourceEvent.getResourceType())
                .resourceId(resourceId)
                .operation(sourceEvent.getOperation())
                .occurredAt(occurredAt)
                .idempotencyKey(idempotencyKey)
                .payload(rawBody)
                .build();
        event = eventRepository.save(event);

        int deliveryCount = 0;
        for (Subscription subscription : subscriptionService.findActiveForSourceEvent(sourceEvent.getId())) {
            deliveryService.deliver(event, subscription, payload);
            deliveryCount++;
        }

        return new IngressResult(event, deliveryCount, false);
    }

    private JsonNode parsePayload(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Request body is not valid JSON", e);
        }
    }

    private void validateSchema(SourceEvent sourceEvent, JsonNode payload) {
        if (sourceEvent.getPayloadSchema() == null || sourceEvent.getPayloadSchema().isBlank()) {
            return;
        }
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema schema = factory.getSchema(sourceEvent.getPayloadSchema());
        Set<ValidationMessage> errors = schema.validate(payload);
        if (!errors.isEmpty()) {
            throw new SchemaValidationException("Payload failed JSON Schema validation: " + errors);
        }
    }

    private String extractRequired(DocumentContext context, String jsonPath, String fieldLabel) {
        try {
            JsonNode value = context.read(jsonPath, JsonNode.class);
            if (value == null || value.isNull()) {
                throw new IllegalArgumentException("%s (%s) resolved to null in the Source payload".formatted(fieldLabel, jsonPath));
            }
            return value.isTextual() ? value.asText() : value.toString();
        } catch (PathNotFoundException e) {
            throw new IllegalArgumentException("%s (%s) not found in the Source payload".formatted(fieldLabel, jsonPath));
        }
    }

    private Instant extractOccurredAt(DocumentContext context, String occurredAtPath) {
        if (occurredAtPath == null || occurredAtPath.isBlank()) {
            return null;
        }
        try {
            JsonNode value = context.read(occurredAtPath, JsonNode.class);
            return value != null && value.isTextual() ? Instant.parse(value.asText()) : null;
        } catch (Exception e) {
            log.warn("Could not extract occurredAt from {}: {}", occurredAtPath, e.getMessage());
            return null;
        }
    }

    private String extractIdempotencyKey(DocumentContext context, SourceEvent sourceEvent, HttpServletRequest request) {
        if (sourceEvent.getIdempotencyHeader() != null && !sourceEvent.getIdempotencyHeader().isBlank()) {
            String header = request.getHeader(sourceEvent.getIdempotencyHeader());
            if (header != null) {
                return header;
            }
        }
        if (sourceEvent.getIdempotencyKeyPath() != null && !sourceEvent.getIdempotencyKeyPath().isBlank()) {
            try {
                JsonNode value = context.read(sourceEvent.getIdempotencyKeyPath(), JsonNode.class);
                return value != null && value.isTextual() ? value.asText() : null;
            } catch (PathNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    /** {@code deduplicated} is true when an idempotency key matched an existing Event — see specs/002-retry-dlq-replay/spec.md. */
    public record IngressResult(Event event, int deliveryCount, boolean deduplicated) {
    }
}
