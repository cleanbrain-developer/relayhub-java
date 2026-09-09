package me.cleanbrain.relayhub.target;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.target.dto.TargetCreateRequest;
import me.cleanbrain.relayhub.target.dto.TargetResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{key}")
    public TargetResponse getByKey(@PathVariable String key) {
        return TargetResponse.from(targetService.getByKey(key));
    }
}
