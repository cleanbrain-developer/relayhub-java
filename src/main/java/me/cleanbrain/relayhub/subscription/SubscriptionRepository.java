package me.cleanbrain.relayhub.subscription;

import me.cleanbrain.relayhub.common.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findBySourceEventIdAndStatus(UUID sourceEventId, Status status);

    /** Eagerly loads {@code target} so the result is safe to use outside the fetching transaction. */
    @Query("select s from Subscription s join fetch s.target where s.sourceEvent.id = :sourceEventId and s.status = :status")
    List<Subscription> findActiveWithTargetBySourceEventId(@Param("sourceEventId") UUID sourceEventId, @Param("status") Status status);
}
