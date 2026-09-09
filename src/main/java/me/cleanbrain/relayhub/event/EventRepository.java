package me.cleanbrain.relayhub.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findBySourceEventId(UUID sourceEventId);

    Optional<Event> findBySourceEventIdAndIdempotencyKey(UUID sourceEventId, String idempotencyKey);
}
