package me.cleanbrain.relayhub.target;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
