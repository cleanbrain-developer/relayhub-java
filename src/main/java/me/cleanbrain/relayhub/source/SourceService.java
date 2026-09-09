package me.cleanbrain.relayhub.source;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.common.Status;
import me.cleanbrain.relayhub.source.dto.SourceCreateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
