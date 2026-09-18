package me.cleanbrain.relayhub.deliverysettings.dto;

/** Ranges (maxAttempts 1..10, autoReplayIntervalMs 5s..1h) are enforced in
 *  DeliverySettingsService, not here — matches this codebase's existing pattern of range/format
 *  checks living in the service layer, not bean validation. Both fields together, not partial
 *  updates — this is the whole singleton settings resource. */
public record DeliverySettingsUpdateRequest(int maxAttempts, long autoReplayIntervalMs) {
}
