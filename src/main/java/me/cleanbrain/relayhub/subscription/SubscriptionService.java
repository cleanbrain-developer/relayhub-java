package me.cleanbrain.relayhub.subscription;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.sourceevent.SourceEventService;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionCreateRequest;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionUpdateRequest;
import me.cleanbrain.relayhub.target.Target;
import me.cleanbrain.relayhub.target.TargetService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SourceEventService sourceEventService;
    private final TargetService targetService;

    @Transactional
    public Subscription create(SubscriptionCreateRequest request) {
        SourceEvent sourceEvent = sourceEventService.getBySourceKeyAndKey(request.sourceKey(), request.sourceEventKey());
        Target target = targetService.getByKey(request.targetKey());

        Subscription subscription = Subscription.builder()
                .sourceEvent(sourceEvent)
                .target(target)
                .name(request.name())
                .description(request.description())
                .targetMethod(request.targetMethod())
                .targetPath(request.targetPath())
                .targetPayloadTemplate(request.targetPayloadTemplate())
                .retryPolicy(request.retryPolicy())
                .status(Status.ACTIVE)
                .build();

        return subscriptionRepository.save(subscription);
    }

    public List<Subscription> findActiveForSourceEvent(UUID sourceEventId) {
        return subscriptionRepository.findBySourceEventIdAndStatus(sourceEventId, Status.ACTIVE);
    }

    public List<Subscription> findAll() {
        return subscriptionRepository.findAll();
    }

    public Subscription getById(UUID id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Subscription not found: " + id));
    }

    @Transactional
    public Subscription update(UUID id, SubscriptionUpdateRequest request) {
        Subscription subscription = getById(id);
        subscription.setName(request.name());
        subscription.setDescription(request.description());
        subscription.setTargetMethod(request.targetMethod());
        subscription.setTargetPath(request.targetPath());
        subscription.setTargetPayloadTemplate(request.targetPayloadTemplate());
        subscription.setRetryPolicy(request.retryPolicy());
        return subscription;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — Delivery history references it. */
    @Transactional
    public void deactivate(UUID id) {
        Subscription subscription = getById(id);
        subscription.setStatus(Status.INACTIVE);
    }
}
