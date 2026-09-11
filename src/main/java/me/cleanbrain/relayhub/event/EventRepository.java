package me.cleanbrain.relayhub.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findBySourceEventId(UUID sourceEventId);

    Optional<Event> findBySourceEventIdAndIdempotencyKey(UUID sourceEventId, String idempotencyKey);

    // A bulk DELETE (not a SELECT + per-entity remove(), which is Spring Data's default for a
    // derived "deleteBy..." method) — this table is the largest and grows fastest at this
    // project's continuous-demo-traffic volume, so per-entity deletion would be needlessly slow.
    // See retention/RetentionCleanupScheduler.java.
    // clearAutomatically: a bulk JPQL delete bypasses the persistence context, so without this,
    // an entity already loaded in the current session could keep appearing "found" via
    // findById's first-level-cache check even after its row is physically gone — this clears
    // that cache so subsequent reads in the same transaction hit the database fresh.
    @Modifying(clearAutomatically = true)
    @Query("delete from Event e where e.receivedAt < :cutoff")
    int deleteByReceivedAtBefore(@Param("cutoff") Instant cutoff);
}
