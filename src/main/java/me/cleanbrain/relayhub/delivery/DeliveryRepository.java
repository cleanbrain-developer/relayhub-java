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

    /** Oldest-DEAD-first, bounded batch for DlqAutoReplayScheduler — oldest first so one
     *  perpetually-broken Target can't starve the rest of the DLQ backlog of ever being retried. */
    List<Delivery> findTop10ByStateOrderByUpdatedAtAsc(DeliveryState state);

    long countByState(DeliveryState state);

    /**
     * Bulk delete — see EventRepository.deleteByReceivedAtBefore for why not a derived delete,
     * and why clearAutomatically.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from Delivery d where d.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
