package me.cleanbrain.relayhub.subscription;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.sourceevent.SourceEvent;
import me.cleanbrain.relayhub.sourceevent.SourceEventService;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionCreateRequest;
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
}
