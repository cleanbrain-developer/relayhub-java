package me.cleanbrain.relayhub.subscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionCreateRequest;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionResponse;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionCreateRequest request) {
        Subscription created = subscriptionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(created));
    }

    @GetMapping
    public List<SubscriptionResponse> list() {
        return subscriptionService.findAll().stream().map(SubscriptionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getById(@PathVariable UUID id) {
        return SubscriptionResponse.from(subscriptionService.getById(id));
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@PathVariable UUID id, @Valid @RequestBody SubscriptionUpdateRequest request) {
        return SubscriptionResponse.from(subscriptionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        subscriptionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
