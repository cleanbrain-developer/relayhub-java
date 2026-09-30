package me.cleanbrain.relayhub.target;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.AuthenticationType;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import me.cleanbrain.relayhub.target.dto.TargetUpdateRequest;
import me.cleanbrain.relayhub.targetendpoint.TargetEndpointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository targetRepository;
    // Repository, not TargetEndpointService — see SourceEventService's own comment on the same
    // pattern (avoids a circular service dependency; TargetEndpointService already depends on
    // TargetService). A Subscription no longer references Target directly (only via
    // TargetEndpoint — see Subscription.java), so hard-deleting every TargetEndpoint first
    // (TargetEndpointService#hardDelete, itself guarded against referencing Subscriptions)
    // transitively guarantees no Subscription is orphaned by deleting a Target — no separate
    // Subscription check needed here.
    private final TargetEndpointRepository targetEndpointRepository;

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
                .authenticationType(request.authenticationType() != null ? request.authenticationType() : AuthenticationType.NONE)
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
        target.setAuthenticationType(request.authenticationType() != null ? request.authenticationType() : AuthenticationType.NONE);
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
     * Permanently removes the row — admin-only. Blocked (409) while any Target Endpoint (any
     * status) still references it, since {@code target_endpoints.target_id} is a real DB foreign
     * key — deactivate/hard-delete those first (which itself is blocked while a Subscription
     * still references the endpoint, see TargetEndpointService#hardDelete).
     */
    @Transactional
    public void hardDelete(String key) {
        Target target = getByKey(key);
        long endpointCount = targetEndpointRepository.countByTarget_Id(target.getId());
        if (endpointCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Target %s: %d Target Endpoint(s) still registered on it"
                            .formatted(key, endpointCount));
        }
        targetRepository.delete(target);
    }
}
