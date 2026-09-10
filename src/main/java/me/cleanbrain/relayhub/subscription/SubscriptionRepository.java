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

    /** Eagerly loads {@code target} so the result is safe to use outside the fetching transaction. */
    @Query("select s from Subscription s join fetch s.target where s.sourceEvent.id = :sourceEventId and s.status = :status")
    List<Subscription> findActiveWithTargetBySourceEventId(@Param("sourceEventId") UUID sourceEventId, @Param("status") Status status);

    /**
     * Eagerly loads {@code sourceEvent} and {@code target} — {@code open-in-view: false} (see
     * application.yml) means the plain {@code findAll()} would otherwise throw
     * LazyInitializationException once the admin console's list endpoint (SubscriptionResponse.from,
     * added in Spec 005) tries to read those associations outside the fetching transaction.
     */
    @Query("select s from Subscription s join fetch s.sourceEvent join fetch s.target")
    List<Subscription> findAllWithDetails();

    /** Same eager-load reasoning as {@link #findAllWithDetails} — for the single-Subscription GET/PUT. */
    @Query("select s from Subscription s join fetch s.sourceEvent join fetch s.target where s.id = :id")
    Optional<Subscription> findWithDetailsById(@Param("id") UUID id);
}
