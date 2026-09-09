package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.delivery.dto.DeliveryAttemptResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryAttemptRepository deliveryAttemptRepository;

    @GetMapping
    public List<DeliveryAttemptResponse> listByEvent(@RequestParam UUID eventId) {
        return deliveryAttemptRepository.findByEventId(eventId).stream()
                .map(DeliveryAttemptResponse::from)
                .toList();
    }
}
