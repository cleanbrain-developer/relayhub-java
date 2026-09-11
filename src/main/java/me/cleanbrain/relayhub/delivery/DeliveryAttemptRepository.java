package me.cleanbrain.relayhub.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByEventId(UUID eventId);

    List<DeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);

    /**
     * Bulk delete — see EventRepository.deleteByReceivedAtBefore for why not a derived delete,
     * and why clearAutomatically.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from DeliveryAttempt a where a.attemptedAt < :cutoff")
    int deleteByAttemptedAtBefore(@Param("cutoff") Instant cutoff);
}
