package me.cleanbrain.relayhub.source;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.source.dto.SourceCreateRequest;
import me.cleanbrain.relayhub.source.dto.SourceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sources")
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;

    @PostMapping
    public ResponseEntity<SourceResponse> create(@Valid @RequestBody SourceCreateRequest request) {
        Source created = sourceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceResponse.from(created));
    }

    @GetMapping("/{key}")
    public SourceResponse getByKey(@PathVariable String key) {
        return SourceResponse.from(sourceService.getByKey(key));
    }
}
