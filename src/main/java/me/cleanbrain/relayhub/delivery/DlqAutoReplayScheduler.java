package me.cleanbrain.relayhub.delivery;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically re-attempts DEAD deliveries automatically, instead of leaving DLQ replay as a
 * purely operator-triggered action (see DeliveryController's manual Replay button / spec.md
 * "Deliberately out of scope" — this is the follow-up that closes that gap). Requested so the
 * admin console's Live page can show DLQ items actually leaving the queue over time, not just
 * accumulating in it.
 *
 * <p>Bounded to 10 oldest DEAD deliveries per tick (see DeliveryRepository.findTop10ByStateOrderByUpdatedAtAsc)
 * and a 30s default interval (relayhub.dlq.auto-replay-interval-ms) so a large or persistently
 * failing backlog can't turn this into a tight retry storm against an already-struggling Target.
 * Each delivery goes through the exact same DeliveryService.replay used by the manual Replay
 * button, so it gets the same one-attempt-per-call semantics and the same live-activity
 * broadcasts (a "delivery" pulse for the attempt, plus a "dlq" pulse again if it's still failing).
 */
@Component
@RequiredArgsConstructor
public class DlqAutoReplayScheduler {

    private static final Logger log = LoggerFactory.getLogger(DlqAutoReplayScheduler.class);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${relayhub.dlq.auto-replay-interval-ms:30000}")
    public void replayDeadDeliveries() {
        List<Delivery> deadDeliveries = deliveryRepository.findTop10ByStateOrderByUpdatedAtAsc(DeliveryState.DEAD);
        for (Delivery delivery : deadDeliveries) {
            try {
                deliveryService.replay(delivery.getId());
            } catch (Exception e) {
                // One delivery's replay failing (e.g. it was manually replayed a moment ago and is
                // no longer DEAD) must not stop the rest of this batch from being attempted.
                log.warn("Auto-replay failed for delivery {}: {}", delivery.getId(), e.getMessage());
            }
        }
    }
}
