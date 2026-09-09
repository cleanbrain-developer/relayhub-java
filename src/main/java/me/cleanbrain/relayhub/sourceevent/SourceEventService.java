package me.cleanbrain.relayhub.sourceevent;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.Source;
import me.cleanbrain.relayhub.source.SourceService;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventCreateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
