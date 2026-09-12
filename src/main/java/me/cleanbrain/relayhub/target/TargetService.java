package me.cleanbrain.relayhub.target;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import me.cleanbrain.relayhub.target.dto.TargetUpdateRequest;
import me.cleanbrain.relayhub.targetfield.TargetFieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository targetRepository;
    // Repository, not SubscriptionService — see SourceEventService's own comment on the same
    // pattern (avoids a circular service dependency; SubscriptionService already depends on
    // TargetService).
    private final SubscriptionRepository subscriptionRepository;
    // Same repository-not-service reasoning — TargetFieldService already depends on this class.
    private final TargetFieldRepository targetFieldRepository;

    @Transactional
    public Target create(TargetCreateRequest request) {
        targetRepository.findByKey(request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException("Target key already registered: " + request.key());
        });
        Target target = Target.builder()
                .key(request.key())
                .name(request.name())
                .description(request.description())
                .baseUrl(request.baseUrl())
                .authenticationConfig(request.authenticationConfig())
                .status(Status.ACTIVE)
                .build();
        return targetRepository.save(target);
    }

    public Target getByKey(String key) {
        return targetRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("Target not found: " + key));
    }

    public List<Target> findAll() {
        return targetRepository.findAll();
    }

    @Transactional
    public Target update(String key, TargetUpdateRequest request) {
        Target target = getByKey(key);
        target.setName(request.name());
        target.setDescription(request.description());
        target.setBaseUrl(request.baseUrl());
        target.setAuthenticationConfig(request.authenticationConfig());
        return target;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — Delivery/Subscription history references it. */
    @Transactional
    public void deactivate(String key) {
        Target target = getByKey(key);
        target.setStatus(Status.INACTIVE);
    }

    /**
     * Permanently removes the row — admin-only. Blocked (409) while any Subscription (any
     * status) still references it, since {@code subscriptions.target_id} is a real DB foreign
     * key — deactivate/hard-delete those Subscriptions first.
     */
    @Transactional
    public void hardDelete(String key) {
        Target target = getByKey(key);
        long subscriptionCount = subscriptionRepository.countByTarget_Id(target.getId());
        if (subscriptionCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Target %s: %d Subscription(s) still reference it"
                            .formatted(key, subscriptionCount));
        }
        long fieldCount = targetFieldRepository.countByTarget_Id(target.getId());
        if (fieldCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Target %s: %d Target Field(s) still registered on it"
                            .formatted(key, fieldCount));
        }
        targetRepository.delete(target);
    }
}
