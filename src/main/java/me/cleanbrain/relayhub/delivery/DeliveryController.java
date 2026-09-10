package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.delivery.dto.DeliveryAttemptResponse;
import me.cleanbrain.relayhub.delivery.dto.DeliveryResponse;
import me.cleanbrain.relayhub.delivery.dto.DeliverySummaryResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryService deliveryService;

    // eventId scopes to one Event's Deliveries (unchanged from before Spec 005 — every existing
    // caller keeps working). Without it, state optionally filters (e.g. DEAD, for the DLQ view);
    // with neither, returns the most recent 200 across all Deliveries — see DeliveryRepository's
    // comment for why 200 and not real pagination.
    @GetMapping
    public List<DeliveryResponse> list(@RequestParam(required = false) UUID eventId,
                                        @RequestParam(required = false) DeliveryState state) {
        List<Delivery> deliveries;
        if (eventId != null) {
            deliveries = deliveryRepository.findByEventId(eventId);
        } else if (state != null) {
            deliveries = deliveryRepository.findTop200ByStateOrderByUpdatedAtDesc(state);
        } else {
            deliveries = deliveryRepository.findTop200ByOrderByUpdatedAtDesc();
        }
        return deliveries.stream().map(DeliveryResponse::from).toList();
    }

    @GetMapping("/summary")
    public DeliverySummaryResponse summary() {
        return new DeliverySummaryResponse(
                deliveryRepository.countByState(DeliveryState.PENDING),
                deliveryRepository.countByState(DeliveryState.SUCCEEDED),
                deliveryRepository.countByState(DeliveryState.DEAD));
    }

    @GetMapping("/{deliveryId}/attempts")
    public List<DeliveryAttemptResponse> listAttempts(@PathVariable UUID deliveryId) {
        if (!deliveryRepository.existsById(deliveryId)) {
            throw new NotFoundException("Delivery not found: " + deliveryId);
        }
        return deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(deliveryId).stream()
                .map(DeliveryAttemptResponse::from)
                .toList();
    }

    @PostMapping("/{deliveryId}/replay")
    public DeliveryResponse replay(@PathVariable UUID deliveryId) {
        return DeliveryResponse.from(deliveryService.replay(deliveryId));
    }
}
