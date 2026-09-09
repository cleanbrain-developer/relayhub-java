package me.cleanbrain.relayhub.sourceevent;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventCreateRequest;
import me.cleanbrain.relayhub.sourceevent.dto.SourceEventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{key}")
    public SourceEventResponse getByKey(@PathVariable String sourceKey, @PathVariable String key) {
        return SourceEventResponse.from(sourceEventService.getBySourceKeyAndKey(sourceKey, key));
    }
}
