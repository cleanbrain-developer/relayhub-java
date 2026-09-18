package me.cleanbrain.relayhub.deliverysettings;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.deliverysettings.dto.DeliverySettingsResponse;
import me.cleanbrain.relayhub.deliverysettings.dto.DeliverySettingsUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET is public (read-only, like DlqScheduleController) so the Deliveries page can always show
 *  the current retry policy; PUT falls under the blanket "/api/** requires ADMIN" rule in
 *  SecurityConfig.java, same as every other write in this console. */
@RestController
@RequestMapping("/api/delivery-settings")
@RequiredArgsConstructor
public class DeliverySettingsController {

    private final DeliverySettingsService deliverySettingsService;

    @GetMapping
    public DeliverySettingsResponse get() {
        return new DeliverySettingsResponse(
                deliverySettingsService.getMaxAttempts(), deliverySettingsService.getAutoReplayIntervalMs());
    }

    @PutMapping
    public DeliverySettingsResponse update(@RequestBody DeliverySettingsUpdateRequest request) {
        deliverySettingsService.updateSettings(request.maxAttempts(), request.autoReplayIntervalMs());
        return get();
    }
}
