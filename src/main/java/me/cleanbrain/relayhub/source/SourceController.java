package me.cleanbrain.relayhub.source;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.source.dto.SourceCreateRequest;
import me.cleanbrain.relayhub.source.dto.SourceResponse;
import me.cleanbrain.relayhub.source.dto.SourceUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public List<SourceResponse> list() {
        return sourceService.findAll().stream().map(SourceResponse::from).toList();
    }

    @GetMapping("/{key}")
    public SourceResponse getByKey(@PathVariable String key) {
        return SourceResponse.from(sourceService.getByKey(key));
    }

    @PutMapping("/{key}")
    public SourceResponse update(@PathVariable String key, @Valid @RequestBody SourceUpdateRequest request) {
        return SourceResponse.from(sourceService.update(key, request));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key, @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            sourceService.hardDelete(key);
        } else {
            sourceService.deactivate(key);
        }
        return ResponseEntity.noContent().build();
    }
}
