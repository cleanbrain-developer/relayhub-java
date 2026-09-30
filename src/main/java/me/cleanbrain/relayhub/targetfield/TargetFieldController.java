package me.cleanbrain.relayhub.targetfield;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldCreateRequest;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldResponse;
import me.cleanbrain.relayhub.targetfield.dto.TargetFieldUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** See specs/006-field-registry/spec.md. Same list/create/edit/soft-delete(+admin hard-delete)
 *  pattern as SourceFieldController, nested one level deeper (fields belong to one TargetEndpoint,
 *  mirroring SourceField belonging to one SourceEvent). */
@RestController
@RequestMapping("/api/targets/{targetKey}/endpoints/{endpointKey}/fields")
@RequiredArgsConstructor
public class TargetFieldController {

    private final TargetFieldService targetFieldService;

    @PostMapping
    public ResponseEntity<TargetFieldResponse> create(@PathVariable String targetKey, @PathVariable String endpointKey,
                                                        @Valid @RequestBody TargetFieldCreateRequest request) {
        TargetField created = targetFieldService.create(targetKey, endpointKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TargetFieldResponse.from(created));
    }

    @GetMapping
    public List<TargetFieldResponse> list(@PathVariable String targetKey, @PathVariable String endpointKey) {
        return targetFieldService.findByTargetKeyAndEndpointKey(targetKey, endpointKey).stream()
                .map(TargetFieldResponse::from).toList();
    }

    @GetMapping("/{fieldKey}")
    public TargetFieldResponse getByKey(@PathVariable String targetKey, @PathVariable String endpointKey,
                                         @PathVariable String fieldKey) {
        return TargetFieldResponse.from(targetFieldService.getByTargetKeyAndEndpointKeyAndFieldKey(targetKey, endpointKey, fieldKey));
    }

    @PutMapping("/{fieldKey}")
    public TargetFieldResponse update(@PathVariable String targetKey, @PathVariable String endpointKey,
                                       @PathVariable String fieldKey, @Valid @RequestBody TargetFieldUpdateRequest request) {
        return TargetFieldResponse.from(targetFieldService.update(targetKey, endpointKey, fieldKey, request));
    }

    @DeleteMapping("/{fieldKey}")
    public ResponseEntity<Void> delete(@PathVariable String targetKey, @PathVariable String endpointKey,
                                        @PathVariable String fieldKey, @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            targetFieldService.hardDelete(targetKey, endpointKey, fieldKey);
        } else {
            targetFieldService.deactivate(targetKey, endpointKey, fieldKey);
        }
        return ResponseEntity.noContent().build();
    }
}
