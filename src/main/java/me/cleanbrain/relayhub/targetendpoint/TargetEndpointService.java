package me.cleanbrain.relayhub.targetendpoint;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.subscription.SubscriptionRepository;
import me.cleanbrain.relayhub.target.Target;
import me.cleanbrain.relayhub.target.TargetService;
import me.cleanbrain.relayhub.targetendpoint.dto.TargetEndpointCreateRequest;
import me.cleanbrain.relayhub.targetendpoint.dto.TargetEndpointUpdateRequest;
import me.cleanbrain.relayhub.targetfield.TargetFieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetEndpointService {

    private final TargetEndpointRepository targetEndpointRepository;
    private final TargetService targetService;
    // Repository, not SubscriptionService — see SourceEventService's own comment on the same
    // pattern (avoids a circular service dependency).
    private final SubscriptionRepository subscriptionRepository;
    // Same repository-not-service reasoning — TargetFieldService already depends on this class.
    private final TargetFieldRepository targetFieldRepository;

    @Transactional
    public TargetEndpoint create(String targetKey, TargetEndpointCreateRequest request) {
        Target target = targetService.getByKey(targetKey);

        targetEndpointRepository.findByTargetKeyAndKey(targetKey, request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException("Target Endpoint already registered: " + targetKey + "/" + request.key());
        });

        TargetEndpoint endpoint = TargetEndpoint.builder()
                .target(target)
                .key(request.key())
                .name(request.name())
                .description(request.description())
                .httpMethod(request.httpMethod())
                .path(request.path())
                .timeoutOverrideMs(request.timeoutOverrideMs())
                .headers(request.headers())
                .status(Status.ACTIVE)
                .build();

        return targetEndpointRepository.save(endpoint);
    }

    public TargetEndpoint getByTargetKeyAndKey(String targetKey, String key) {
        return targetEndpointRepository.findByTargetKeyAndKey(targetKey, key)
                .orElseThrow(() -> new NotFoundException("Target Endpoint not found: " + targetKey + "/" + key));
    }

    public List<TargetEndpoint> findByTargetKey(String targetKey) {
        return targetEndpointRepository.findByTargetKey(targetKey);
    }

    @Transactional
    public TargetEndpoint update(String targetKey, String key, TargetEndpointUpdateRequest request) {
        TargetEndpoint endpoint = getByTargetKeyAndKey(targetKey, key);
        endpoint.setName(request.name());
        endpoint.setDescription(request.description());
        endpoint.setHttpMethod(request.httpMethod());
        endpoint.setPath(request.path());
        endpoint.setTimeoutOverrideMs(request.timeoutOverrideMs());
        endpoint.setHeaders(request.headers());
        return endpoint;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — Subscription/Delivery history references it. */
    @Transactional
    public void deactivate(String targetKey, String key) {
        TargetEndpoint endpoint = getByTargetKeyAndKey(targetKey, key);
        endpoint.setStatus(Status.INACTIVE);
    }

    /**
     * Permanently removes the row — admin-only. Blocked (409) while any Subscription (any status)
     * or TargetField (any status) still references it, since both
     * {@code subscriptions.target_endpoint_id} and {@code target_fields.target_endpoint_id} are
     * real DB foreign keys — deactivate/hard-delete those first.
     */
    @Transactional
    public void hardDelete(String targetKey, String key) {
        TargetEndpoint endpoint = getByTargetKeyAndKey(targetKey, key);
        long subscriptionCount = subscriptionRepository.countByTargetEndpoint_Id(endpoint.getId());
        if (subscriptionCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Target Endpoint %s/%s: %d Subscription(s) still reference it"
                            .formatted(targetKey, key, subscriptionCount));
        }
        long fieldCount = targetFieldRepository.countByTargetEndpoint_Id(endpoint.getId());
        if (fieldCount > 0) {
            throw new IllegalStateException(
                    "Cannot permanently delete Target Endpoint %s/%s: %d Target Field(s) still registered on it"
                            .formatted(targetKey, key, fieldCount));
        }
        targetEndpointRepository.delete(endpoint);
    }
}
