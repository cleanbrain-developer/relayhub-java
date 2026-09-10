package me.cleanbrain.relayhub.sourceevent;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.Source;
import me.cleanbrain.relayhub.source.SourceService;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventCreateRequest;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceEventService {

    private final SourceEventRepository sourceEventRepository;
    private final SourceService sourceService;

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
}
