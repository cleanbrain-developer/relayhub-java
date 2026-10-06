package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import me.cleanbrain.relayhub.common.NotFoundException;
import me.cleanbrain.relayhub.delivery.dto.BulkReplayResponse;
import me.cleanbrain.relayhub.delivery.dto.DeliveryAttemptResponse;
import me.cleanbrain.relayhub.delivery.dto.DeliveryResponse;
import me.cleanbrain.relayhub.delivery.dto.DeliverySummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private static final Logger log = LoggerFactory.getLogger(DeliveryController.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final DeliveryService deliveryService;

    // eventId scopes to one Event's Deliveries (unchanged from before Spec 005 — every existing
    // caller keeps working) and takes precedence over everything else. Otherwise subscriptionId
    // and/or targetId optionally narrow to one Subscription/Target (subscriptionId wins if both
    // are given, since a Subscription always implies exactly one Target but not the reverse —
    // scale-out readiness review, 2026-10-06), and state optionally filters further on top of
    // either. With none of the three, returns the most recent 200 across all Deliveries — see
    // DeliveryRepository's comment for why 200 and not real pagination.
    @GetMapping
    public List<DeliveryResponse> list(@RequestParam(required = false) UUID eventId,
                                        @RequestParam(required = false) UUID subscriptionId,
                                        @RequestParam(required = false) UUID targetId,
                                        @RequestParam(required = false) DeliveryState state) {
        List<Delivery> deliveries;
        if (eventId != null) {
            deliveries = deliveryRepository.findByEventId(eventId);
        } else if (subscriptionId != null) {
            deliveries = state != null
                    ? deliveryRepository.findTop200BySubscriptionIdAndStateOrderByUpdatedAtDesc(subscriptionId, state)
                    : deliveryRepository.findTop200BySubscriptionIdOrderByUpdatedAtDesc(subscriptionId);
        } else if (targetId != null) {
            deliveries = state != null
                    ? deliveryRepository.findTop200ByTargetIdAndStateOrderByUpdatedAtDesc(targetId, state)
                    : deliveryRepository.findTop200ByTargetIdOrderByUpdatedAtDesc(targetId);
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
                deliveryRepository.countByStateIn(java.util.List.of(
                        DeliveryState.PENDING, DeliveryState.PROCESSING, DeliveryState.RETRYING, DeliveryState.REPLAYING)),
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

    // Admin-only (SecurityConfig: non-GET /api/** requires ADMIN) — replays up to 50 currently-DEAD
    // Deliveries in one call, oldest-first, optionally scoped to one Target or Subscription (same
    // subscriptionId-wins-over-targetId precedence as list()). Same real-HTTP-attempt-per-Delivery
    // semantics as the single-replay endpoint, just looped — each replay() call goes through the
    // injected deliveryService bean (not a self-invocation), so each still gets its own @Transactional
    // boundary. One failing replay (e.g. a referenced Event/Subscription hard-deleted mid-batch) is
    // caught and counted rather than aborting the rest of the batch (scale-out readiness review,
    // 2026-10-06 finding: no bulk DLQ action existed at all — every replay required its own click).
    @PostMapping("/bulk-replay")
    public BulkReplayResponse bulkReplay(@RequestParam(required = false) UUID subscriptionId,
                                          @RequestParam(required = false) UUID targetId) {
        List<Delivery> candidates;
        if (subscriptionId != null) {
            candidates = deliveryRepository.findTop50BySubscriptionIdAndStateOrderByUpdatedAtAsc(subscriptionId, DeliveryState.DEAD);
        } else if (targetId != null) {
            candidates = deliveryRepository.findTop50ByTargetIdAndStateOrderByUpdatedAtAsc(targetId, DeliveryState.DEAD);
        } else {
            candidates = deliveryRepository.findTop50ByStateOrderByUpdatedAtAsc(DeliveryState.DEAD);
        }

        int succeeded = 0;
        int stillDead = 0;
        int errors = 0;
        for (Delivery candidate : candidates) {
            try {
                Delivery result = deliveryService.replay(candidate.getId());
                if (result.getState() == DeliveryState.DEAD) {
                    stillDead++;
                } else {
                    succeeded++;
                }
            } catch (Exception e) {
                errors++;
                log.warn("Bulk replay: Delivery {} failed to replay", candidate.getId(), e);
            }
        }
        return new BulkReplayResponse(candidates.size(), succeeded, stillDead, errors);
    }
}
