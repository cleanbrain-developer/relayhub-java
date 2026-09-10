package me.cleanbrain.relayhub.target;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import me.cleanbrain.relayhub.target.dto.TargetUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository targetRepository;

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
}
