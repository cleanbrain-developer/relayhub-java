package me.cleanbrain.relayhub.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * Bulk delete — see EventRepository.deleteByReceivedAtBefore for why not a derived delete,
     * and why clearAutomatically.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from OutboxEvent o where o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
