package me.cleanbrain.relayhub.targetendpoint;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.targetendpoint.dto.TargetEndpointCreateRequest;
import me.cleanbrain.relayhub.targetendpoint.dto.TargetEndpointResponse;
import me.cleanbrain.relayhub.targetendpoint.dto.TargetEndpointUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/targets/{targetKey}/endpoints")
@RequiredArgsConstructor
public class TargetEndpointController {

    private final TargetEndpointService targetEndpointService;

    @PostMapping
    public ResponseEntity<TargetEndpointResponse> create(@PathVariable String targetKey,
                                                            @Valid @RequestBody TargetEndpointCreateRequest request) {
        TargetEndpoint created = targetEndpointService.create(targetKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TargetEndpointResponse.from(created));
    }

    @GetMapping
    public List<TargetEndpointResponse> list(@PathVariable String targetKey) {
        return targetEndpointService.findByTargetKey(targetKey).stream().map(TargetEndpointResponse::from).toList();
    }

    @GetMapping("/{key}")
    public TargetEndpointResponse getByKey(@PathVariable String targetKey, @PathVariable String key) {
        return TargetEndpointResponse.from(targetEndpointService.getByTargetKeyAndKey(targetKey, key));
    }

    @PutMapping("/{key}")
    public TargetEndpointResponse update(@PathVariable String targetKey, @PathVariable String key,
                                          @Valid @RequestBody TargetEndpointUpdateRequest request) {
        return TargetEndpointResponse.from(targetEndpointService.update(targetKey, key, request));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String targetKey, @PathVariable String key,
                                        @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            targetEndpointService.hardDelete(targetKey, key);
        } else {
            targetEndpointService.deactivate(targetKey, key);
        }
        return ResponseEntity.noContent().build();
    }
}
