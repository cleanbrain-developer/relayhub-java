package me.cleanbrain.relayhub.subscription;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.deliverysettings.DeliverySettingsService;
import me.cleanbrain.relayhub.mapping.MappingValidationService;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.sourceevent.SourceEventService;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionCreateRequest;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionUpdateRequest;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpoint;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpointService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SourceEventService sourceEventService;
    private final TargetEndpointService targetEndpointService;
    private final DeliverySettingsService deliverySettingsService;
    private final MappingValidationService mappingValidationService;

    @Transactional
    public Subscription create(SubscriptionCreateRequest request) {
        SourceEvent sourceEvent = sourceEventService.getBySourceKeyAndKey(request.sourceKey(), request.sourceEventKey());
        TargetEndpoint targetEndpoint = targetEndpointService.getByTargetKeyAndKey(request.targetKey(), request.targetEndpointKey());

        Subscription subscription = Subscription.builder()
                .sourceEvent(sourceEvent)
                .targetEndpoint(targetEndpoint)
                .name(request.name())
                .description(request.description())
                .targetPayloadTemplate(request.targetPayloadTemplate())
                .filterExpression(request.filterExpression())
                .maxAttempts(request.maxAttempts())
                .initialBackoffMs(request.initialBackoffMs())
                .maxBackoffMs(request.maxBackoffMs())
                .backoffMultiplier(request.backoffMultiplier())
                .jitter(request.jitter())
                .timeoutMs(request.timeoutMs())
                .status(Status.ACTIVE)
                .build();

        return subscriptionRepository.save(subscription);
    }

    public List<Subscription> findActiveForSourceEvent(UUID sourceEventId) {
        return subscriptionRepository.findBySourceEventIdAndStatus(sourceEventId, Status.ACTIVE);
    }

    public List<Subscription> findAll() {
        return subscriptionRepository.findAllWithDetails();
    }

    public Subscription getById(UUID id) {
        return subscriptionRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
    }

    /** {@link me.cleanbrain.relayhub.subscription.dto.SubscriptionResponse}'s effectiveMaxAttempts
     *  — resolves the Subscription's own override against the global default. Public (not folded
     *  into getById) so the controller can compute it once per response without a second lookup. */
    public int resolveEffectiveMaxAttempts(Subscription subscription) {
        return subscription.getMaxAttempts() != null ? subscription.getMaxAttempts() : deliverySettingsService.getMaxAttempts();
    }

    /** {@link me.cleanbrain.relayhub.subscription.dto.SubscriptionResponse}'s mappingWarnings —
     *  see {@link MappingValidationService} for what's actually checked. Warning-only: never
     *  blocks create/update, same as {@link #resolveEffectiveMaxAttempts} this mirrors the shape
     *  of. */
    public List<String> resolveMappingWarnings(Subscription subscription) {
        return mappingValidationService.validate(
                subscription.getSourceEvent(), subscription.getTargetEndpoint(), subscription.getTargetPayloadTemplate());
    }

    @Transactional
    public Subscription update(UUID id, SubscriptionUpdateRequest request) {
        Subscription subscription = getById(id);
        subscription.setName(request.name());
        subscription.setDescription(request.description());
        subscription.setTargetPayloadTemplate(request.targetPayloadTemplate());
        subscription.setFilterExpression(request.filterExpression());
        subscription.setMaxAttempts(request.maxAttempts());
        subscription.setInitialBackoffMs(request.initialBackoffMs());
        subscription.setMaxBackoffMs(request.maxBackoffMs());
        subscription.setBackoffMultiplier(request.backoffMultiplier());
        subscription.setJitter(request.jitter());
        subscription.setTimeoutMs(request.timeoutMs());
        return subscription;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — Delivery history references it. */
    @Transactional
    public void deactivate(UUID id) {
        Subscription subscription = getById(id);
        subscription.setStatus(Status.INACTIVE);
    }

    /**
     * Permanently removes the row — admin-only (see security/SecurityConfig.java), on top of
     * (not instead of) {@link #deactivate}. Always safe at the DB level: nothing has a foreign
     * key to a Subscription (see db/migration/V1__init_schema.sql) — Delivery/Event/OutboxEvent
     * reference it by plain UUID, same already-accepted trade-off as the retention cleanup job.
     */
    @Transactional
    public void hardDelete(UUID id) {
        Subscription subscription = getById(id);
        subscriptionRepository.delete(subscription);
    }
}
