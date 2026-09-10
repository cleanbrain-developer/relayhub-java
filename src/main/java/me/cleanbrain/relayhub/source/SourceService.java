package me.cleanbrain.relayhub.source;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.dto.SourceCreateRequest;
import me.cleanbrain.relayhub.source.dto.SourceUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;

    @Transactional
    public Source create(SourceCreateRequest request) {
        sourceRepository.findByKey(request.key()).ifPresent(existing -> {
            throw new IllegalArgumentException("Source key already registered: " + request.key());
        });
        Source source = Source.builder()
                .key(request.key())
                .name(request.name())
                .description(request.description())
                .authenticationConfig(request.authenticationConfig())
                .status(Status.ACTIVE)
                .build();
        return sourceRepository.save(source);
    }

    public Source getByKey(String key) {
        return sourceRepository.findByKey(key)
                .orElseThrow(() -> new NotFoundException("Source not found: " + key));
    }

    public List<Source> findAll() {
        return sourceRepository.findAll();
    }

    @Transactional
    public Source update(String key, SourceUpdateRequest request) {
        Source source = getByKey(key);
        source.setName(request.name());
        source.setDescription(request.description());
        source.setAuthenticationConfig(request.authenticationConfig());
        return source;
    }

    /** Soft delete: flips status to INACTIVE. Row stays — Delivery/Event history references it. */
    @Transactional
    public void deactivate(String key) {
        Source source = getByKey(key);
        source.setStatus(Status.INACTIVE);
    }
}
