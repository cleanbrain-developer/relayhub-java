package me.cleanbrain.relayhub.subscription;

import me.cleanbrain.relayhub.common.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findBySourceEventIdAndStatus(UUID sourceEventId, Status status);

    /**
     * Eagerly loads {@code target} and {@code sourceEvent.source} so the result is safe to use
     * outside the fetching transaction. The latter was added once {@code DeliveryService.attemptOnce}
     * started reading {@code subscription.getSourceEvent().getSource().getKey()} (for the live-
     * activity SSE stream, see live/LiveEvent.java) — a detached {@code Subscription} passed into
     * {@code deliver()} (exactly what this method exists to produce, for redelivery-simulation
     * tests) would otherwise throw LazyInitializationException on that access, caught live by
     * DeliveryDedupTest.
     */
    @Query("select s from Subscription s join fetch s.target join fetch s.sourceEvent se join fetch se.source where s.sourceEvent.id = :sourceEventId and s.status = :status")
    List<Subscription> findActiveWithTargetBySourceEventId(@Param("sourceEventId") UUID sourceEventId, @Param("status") Status status);

    /**
     * Eagerly loads {@code sourceEvent}, {@code sourceEvent.source} and {@code target} — {@code
     * open-in-view: false} (see application.yml) means the plain {@code findAll()} would otherwise
     * throw LazyInitializationException once the admin console's list endpoint
     * (SubscriptionResponse.from, added in Spec 005) tries to read those associations outside the
     * fetching transaction. {@code sourceEvent.source} was added once SubscriptionResponse started
     * exposing {@code sourceKey} (for the Live page's Source-&gt;Event topology, see live/LiveEvent.java).
     */
    @Query("select s from Subscription s join fetch s.sourceEvent se join fetch se.source join fetch s.target")
    List<Subscription> findAllWithDetails();

    /** Same eager-load reasoning as {@link #findAllWithDetails} — for the single-Subscription GET/PUT. */
    @Query("select s from Subscription s join fetch s.sourceEvent se join fetch se.source join fetch s.target where s.id = :id")
    Optional<Subscription> findWithDetailsById(@Param("id") UUID id);

    // Hard-delete guards (any status, not just ACTIVE — an INACTIVE Subscription still holds a
    // real FK to its SourceEvent/Target, so it would still block a hard delete of either).
    long countBySourceEvent_Id(UUID sourceEventId);

    long countByTarget_Id(UUID targetId);
}
