package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.delivery.dto.DeliveryAttemptResponse;
import me.cleanbrain.relayhub.delivery.dto.DeliveryResponse;
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

    @GetMapping
    public List<DeliveryResponse> listByEvent(@RequestParam UUID eventId) {
        return deliveryRepository.findByEventId(eventId).stream()
                .map(DeliveryResponse::from)
                .toList();
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
