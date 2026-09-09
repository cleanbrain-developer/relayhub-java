package me.cleanbrain.relayhub.subscription;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionCreateRequest;
import me.cleanbrain.relayhub.subscription.dto.SubscriptionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
