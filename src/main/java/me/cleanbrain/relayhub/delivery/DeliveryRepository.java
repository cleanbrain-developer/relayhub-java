package me.cleanbrain.relayhub.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    List<Delivery> findByEventId(UUID eventId);

    Optional<Delivery> findByEventIdAndSubscriptionId(UUID eventId, UUID subscriptionId);

    // No pagination yet (see specs/005-admin-console/spec.md "Deliberately out of scope") — capped
    // at the most recent 200 instead, which is enough to be useful at this project's data volume
    // without risking an unbounded payload as demo traffic accumulates over time.
    List<Delivery> findTop200ByOrderByUpdatedAtDesc();

    List<Delivery> findTop200ByStateOrderByUpdatedAtDesc(DeliveryState state);

    // Target/Subscription filters for the Deliveries console (scale-out readiness review,
    // 2026-10-06 finding: the state filter was the only one, so an operator chasing "why does
    // Target X keep failing" had to scroll the unfiltered top-200 list by eye). subscriptionId
    // takes precedence over targetId when both are given (DeliveryController), since a
    // Subscription always implies exactly one Target but not the reverse.
    List<Delivery> findTop200ByTargetIdOrderByUpdatedAtDesc(UUID targetId);

    List<Delivery> findTop200ByTargetIdAndStateOrderByUpdatedAtDesc(UUID targetId, DeliveryState state);

    List<Delivery> findTop200BySubscriptionIdOrderByUpdatedAtDesc(UUID subscriptionId);

    List<Delivery> findTop200BySubscriptionIdAndStateOrderByUpdatedAtDesc(UUID subscriptionId, DeliveryState state);

    // Bulk-replay batch (scale-out readiness review, 2026-10-06 finding: operators had to replay
    // DEAD deliveries one at a time, even when chasing down a single Target's whole backlog).
    // Oldest-first and capped at 50, same "don't starve the backlog, bound one request's worst-case
    // latency" reasoning as DlqAutoReplayScheduler's own findTop10By...OrderByUpdatedAtAsc — each
    // replay is a real synchronous HTTP attempt, so an unbounded batch could make one API call take
    // minutes.
    List<Delivery> findTop50ByStateOrderByUpdatedAtAsc(DeliveryState state);

    List<Delivery> findTop50ByTargetIdAndStateOrderByUpdatedAtAsc(UUID targetId, DeliveryState state);

    List<Delivery> findTop50BySubscriptionIdAndStateOrderByUpdatedAtAsc(UUID subscriptionId, DeliveryState state);

    /** Oldest-DEAD-first, bounded batch for DlqAutoReplayScheduler — oldest first so one
     *  perpetually-broken Target can't starve the rest of the DLQ backlog of ever being retried. */
    List<Delivery> findTop10ByStateOrderByUpdatedAtAsc(DeliveryState state);

    /** Due-first, bounded batch for DeliveryRetryScheduler — deliveries whose backoff has elapsed
     *  (state RETRYING, nextAttemptAt <= now). Soonest-due first, same "don't starve the rest of
     *  the batch" reasoning as findTop10ByStateOrderByUpdatedAtAsc. */
    List<Delivery> findTop20ByStateAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(DeliveryState state, Instant now);

    long countByState(DeliveryState state);

    /** Used by DeliveryController's summary endpoint to report one "in flight" bucket across every
     *  non-terminal state (PENDING/PROCESSING/RETRYING/REPLAYING) instead of just PENDING, which
     *  Stage 2's async retry loop now passes through almost instantly. */
    long countByStateIn(java.util.Collection<DeliveryState> states);

    /**
     * Bulk delete — see EventRepository.deleteByReceivedAtBefore for why not a derived delete,
     * and why clearAutomatically.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from Delivery d where d.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
