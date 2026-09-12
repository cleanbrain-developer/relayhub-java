package me.cleanbrain.relayhub.sourcefield;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.sourcefield.dto.SourceFieldCreateRequest;
import me.cleanbrain.relayhub.sourcefield.dto.SourceFieldResponse;
import me.cleanbrain.relayhub.sourcefield.dto.SourceFieldUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** See specs/006-field-registry/spec.md. Same list/create/edit/soft-delete(+admin hard-delete)
 *  pattern as SourceEventController, nested one level deeper (fields belong to one SourceEvent). */
@RestController
@RequestMapping("/api/sources/{sourceKey}/events/{eventKey}/fields")
@RequiredArgsConstructor
public class SourceFieldController {

    private final SourceFieldService sourceFieldService;

    @PostMapping
    public ResponseEntity<SourceFieldResponse> create(@PathVariable String sourceKey, @PathVariable String eventKey,
                                                        @Valid @RequestBody SourceFieldCreateRequest request) {
        SourceField created = sourceFieldService.create(sourceKey, eventKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SourceFieldResponse.from(created));
    }

    @GetMapping
    public List<SourceFieldResponse> list(@PathVariable String sourceKey, @PathVariable String eventKey) {
        return sourceFieldService.findBySourceKeyAndEventKey(sourceKey, eventKey).stream()
                .map(SourceFieldResponse::from).toList();
    }

    @GetMapping("/{fieldKey}")
    public SourceFieldResponse getByKey(@PathVariable String sourceKey, @PathVariable String eventKey,
                                         @PathVariable String fieldKey) {
        return SourceFieldResponse.from(sourceFieldService.getBySourceKeyAndEventKeyAndFieldKey(sourceKey, eventKey, fieldKey));
    }

    @PutMapping("/{fieldKey}")
    public SourceFieldResponse update(@PathVariable String sourceKey, @PathVariable String eventKey,
                                       @PathVariable String fieldKey, @Valid @RequestBody SourceFieldUpdateRequest request) {
        return SourceFieldResponse.from(sourceFieldService.update(sourceKey, eventKey, fieldKey, request));
    }

    @DeleteMapping("/{fieldKey}")
    public ResponseEntity<Void> delete(@PathVariable String sourceKey, @PathVariable String eventKey,
                                        @PathVariable String fieldKey, @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            sourceFieldService.hardDelete(sourceKey, eventKey, fieldKey);
        } else {
            sourceFieldService.deactivate(sourceKey, eventKey, fieldKey);
        }
        return ResponseEntity.noContent().build();
    }
}
