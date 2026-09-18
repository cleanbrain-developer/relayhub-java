package me.cleanbrain.relayhub.deliverysettings.dto;

/** Range (1..10) is enforced in DeliverySettingsService, not here — matches this codebase's
 *  existing pattern of range/format checks living in the service layer, not bean validation. */
public record DeliverySettingsUpdateRequest(int maxAttempts) {
}
