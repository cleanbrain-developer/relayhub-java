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
     * Postgres transaction-scoped advisory lock (self-review finding, 2026-09-17): guards
     * OutboxPublisher.publishPending() against more than one replica racing to publish the same
     * PENDING rows to Kafka before either commits its PUBLISHED status flip. Safe to scope to the
     * current transaction (unlike DlqAutoReplayScheduler's session-level lock) because this
     * repository call runs inside publishPending()'s own single @Transactional method — the lock
     * only needs to outlive that one transaction, and Postgres releases it automatically at
     * commit/rollback.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryXactLock(@Param("key") long key);

    /**
     * Bulk delete — see EventRepository.deleteByReceivedAtBefore for why not a derived delete,
     * and why clearAutomatically.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from OutboxEvent o where o.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
