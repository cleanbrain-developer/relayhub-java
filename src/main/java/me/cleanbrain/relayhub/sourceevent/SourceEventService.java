package me.cleanbrain.relayhub.sourceevent;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.Source;
import me.cleanbrain.relayhub.source.SourceService;
import me.cleanbrain.relayhub.sourcefield.SourceFieldRepository;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventCreateRequest;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventUpdateRequest;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceEventService {

    private final SourceEventRepository sourceEventRepository;
    private final SourceService sourceService;
    // Repository, not SubscriptionService — SubscriptionService already depends on this class
    // (to resolve a Subscription's SourceEvent by key), so depending back on the service would
    // be circular. The repository-level dependency avoids that while still letting hardDelete
    // check for referencing Subscriptions below.
    private final SubscriptionRepository subscriptionRepository;
    // Same repository-not-service reasoning: SourceFieldService already depends on this class to
    // resolve a field's owning SourceEvent.
    private final SourceFieldRepository sourceFieldRepository;

    @Transactional
    public SourceEvent create(String sourceKey, SourceEventCreateRequest request) {
        Source source = sourceService.getByKey(sourceKey);

        sourceEventRepository.findBySourceKeyAndKey(sourceKey, request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException("Source Event already registered: " + sourceKey + "/" + request.key());
        });

        // Ingress URL is generated from registration data, never hardcoded — see docs/architecture/system-design.md.
        String ingressPath = "/ingress/v1/%s/%s".formatted(source.getKey(), request.key());

        SourceEvent sourceEvent = SourceEvent.builder()
                .source(source)
                .key(request.key())
                .name(request.name())
                .description(request.description())
                .resourceType(request.resourceType())
                .operation(request.operation())
                .ingressMethod(request.operation().ingressMethod())
                .ingressPath(ingressPath)
                .resourceIdPath(request.resourceIdPath())
                .occurredAtPath(request.occurredAtPath())
                .idempotencyKeyPath(request.idempotencyKeyPath())
                .idempotencyHeader(request.idempotencyHeader())
                .payloadSchema(request.payloadSchema())
                .status(Status.ACTIVE)
                .build();

        return sourceEventRepository.save(sourceEvent);
    }

    public SourceEvent getBySourceKeyAndKey(String sourceKey, String key) {
        return sourceEventRepository.findBySourceKeyAndKey(sourceKey, key)
                .orElseThrow(() -> new NotFoundException("Source Event not found: " + sourceKey + "/" + key));
    }

    public List<SourceEvent> findBySourceKey(String sourceKey) {
        return sourceEventRepository.findBySourceKey(sourceKey);
    }

    @Transactional
    public SourceEvent update(String sourceKey, String key, SourceEventUpdateRequest request) {
        SourceEvent sourceEvent = getBySourceKeyAndKey(sourceKey, key);
        sourceEvent.setName(request.name());
        sourceEvent.setDescription(request.description());
        sourceEvent.setResourceType(request.resourceType());
        sourceEvent.setOperation(request.operation());
        // ingressPath stays the same (derived from source.key + key, both immutable), but the
        // HTTP verb the Ingress endpoint accepts is derived from Operation — see IngressController.
        sourceEvent.setIngressMethod(request.operation().ingressMethod());
        sourceEvent.setResourceIdPath(request.resourceIdPath());
        sourceEvent.setOccurredAtPath(request.occurredAtPath());
        sourceEvent.setIdempotencyKeyPath(request.idempotencyKeyPath());
        sourceEvent.setIdempotencyHeader(request.idempotencyHeader());
        sourceEvent.setPayloadSchema(request.payloadSchema());
        return sourceEvent;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — Subscription/Event history references it. */
    @Transactional
    public void deactivate(String sourceKey, String key) {
        SourceEvent sourceEvent = getBySourceKeyAndKey(sourceKey, key);
        sourceEvent.setStatus(Status.INACTIVE);
    }

    /**
     * Permanently removes the row — admin-only. Blocked (409, via IllegalStateException — see
     * GlobalExceptionHandler) while any Subscription (any status) or SourceField (any status)
     * still references it, since both {@code subscriptions.source_event_id} and
     * {@code source_fields.source_event_id} are real DB foreign keys (see
     * db/migration/V1__init_schema.sql, V2__field_registry.sql) — deactivate/hard-delete those first.
     */
    @Transactional
    public void hardDelete(String sourceKey, String key) {
        SourceEvent sourceEvent = getBySourceKeyAndKey(sourceKey, key);
        long subscriptionCount = subscriptionRepository.countBySourceEvent_Id(sourceEvent.getId());
        if (subscriptionCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Source Event %s/%s: %d Subscription(s) still reference it"
                            .formatted(sourceKey, key, subscriptionCount));
        }
        long fieldCount = sourceFieldRepository.countBySourceEvent_Id(sourceEvent.getId());
        if (fieldCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Source Event %s/%s: %d Source Field(s) still registered on it"
                            .formatted(sourceKey, key, fieldCount));
        }
        sourceEventRepository.delete(sourceEvent);
    }
}
