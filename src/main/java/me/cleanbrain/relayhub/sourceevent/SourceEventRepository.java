package me.cleanbrain.relayhub.sourceevent;

import me.cleanbrain.relayhub.common.HttpVerb;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceEventRepository extends JpaRepository<SourceEvent, UUID> {

    Optional<SourceEvent> findBySourceKeyAndKey(String sourceKey, String key);

    Optional<SourceEvent> findByIngressPathAndIngressMethod(String ingressPath, HttpVerb ingressMethod);
}
