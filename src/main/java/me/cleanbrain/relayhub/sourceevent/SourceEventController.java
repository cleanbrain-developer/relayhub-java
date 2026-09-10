package me.cleanbrain.relayhub.sourceevent;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventCreateRequest;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventResponse;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sources/{sourceKey}/events")
@RequiredArgsConstructor
public class SourceEventController {

    private final SourceEventService sourceEventService;

    @PostMapping
    public ResponseEntity<SourceEventResponse> create(@PathVariable String sourceKey,
                                                        @Valid @RequestBody SourceEventCreateRequest request) {
        SourceEvent created = sourceEventService.create(sourceKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceEventResponse.from(created));
    }

    @GetMapping
    public List<SourceEventResponse> list(@PathVariable String sourceKey) {
        return sourceEventService.findBySourceKey(sourceKey).stream().map(SourceEventResponse::from).toList();
    }

    @GetMapping("/{key}")
    public SourceEventResponse getByKey(@PathVariable String sourceKey, @PathVariable String key) {
        return SourceEventResponse.from(sourceEventService.getBySourceKeyAndKey(sourceKey, key));
    }

    @PutMapping("/{key}")
    public SourceEventResponse update(@PathVariable String sourceKey, @PathVariable String key,
                                       @Valid @RequestBody SourceEventUpdateRequest request) {
        return SourceEventResponse.from(sourceEventService.update(sourceKey, key, request));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deactivate(@PathVariable String sourceKey, @PathVariable String key) {
        sourceEventService.deactivate(sourceKey, key);
        return ResponseEntity.noContent().build();
    }
}
