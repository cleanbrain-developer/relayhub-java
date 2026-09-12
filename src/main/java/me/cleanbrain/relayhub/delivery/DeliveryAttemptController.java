package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.delivery.dto.DeliveryAttemptResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Standalone attempt lookup by id, separate from {@code GET /api/deliveries/{deliveryId}/attempts}
 * — the Live page only ever learns an attempt's id (via the SSE feed's {@code attemptId}, see
 * live/LiveEvent.java), never its parent Delivery's id, so it needs this to show the same
 * request/response detail view the Deliveries page shows from the nested list. Public, same as
 * every other GET (see security/SecurityConfig.java).
 */
@RestController
@RequestMapping("/api/delivery-attempts")
@RequiredArgsConstructor
public class DeliveryAttemptController {

    private final DeliveryAttemptRepository deliveryAttemptRepository;

    @GetMapping("/{id}")
    public DeliveryAttemptResponse get(@PathVariable UUID id) {
        DeliveryAttempt attempt = deliveryAttemptRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Delivery attempt not found: " + id));
        return DeliveryAttemptResponse.from(attempt);
    }
}
