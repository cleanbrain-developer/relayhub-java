package me.cleanbrain.relayhub.subscription;

import me.cleanbrain.relayhub.common.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findBySourceEventIdAndStatus(UUID sourceEventId, Status status);
}
