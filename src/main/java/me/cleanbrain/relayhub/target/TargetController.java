package me.cleanbrain.relayhub.target;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import me.cleanbrain.relayhub.target.dto.TargetResponse;
import me.cleanbrain.relayhub.target.dto.TargetUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class TargetController {

    private final TargetService targetService;

    @PostMapping
    public ResponseEntity<TargetResponse> create(@Valid @RequestBody TargetCreateRequest request) {
        Target created = targetService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TargetResponse.from(created));
    }

    @GetMapping
    public List<TargetResponse> list() {
        return targetService.findAll().stream().map(TargetResponse::from).toList();
    }

    @GetMapping("/{key}")
    public TargetResponse getByKey(@PathVariable String key) {
        return TargetResponse.from(targetService.getByKey(key));
    }

    @PutMapping("/{key}")
    public TargetResponse update(@PathVariable String key, @Valid @RequestBody TargetUpdateRequest request) {
        return TargetResponse.from(targetService.update(key, request));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key, @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            targetService.hardDelete(key);
        } else {
            targetService.deactivate(key);
        }
        return ResponseEntity.noContent().build();
    }
}
